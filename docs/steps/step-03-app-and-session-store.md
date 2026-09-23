# Step 3 — `App` singleton holder + `SessionStore`

Goal: two pieces of plumbing every screen will use.

1. **`SessionStore`** saves the in-progress scanning session to a JSON file so that closing the app
   (or a crash, or the CN51's battery dying) does not lose the items. On launch, a non-empty file
   means "continue session".
2. **`App`** is the one object that lives as long as the process. It creates the `InventoryApi` and
   the `SessionStore` once and hands them to any screen that asks.

Done when `./gradlew test` passes 9 tests and `./gradlew assembleDebug` still builds.

## Concepts you meet in this step

| Concept | One-liner |
|---|---|
| `Application` subclass | Android creates exactly one before any screen. Good home for shared objects. Must be registered in the manifest. |
| `Context` | Android's handle to "the app environment": files dir, resources, starting screens. Every Activity is a Context. |
| `File` | A path, not the contents. `new File(dir, "x.json")` builds a path; `exists()`, `delete()` act on it. |
| try-with-resources | `try (Reader r = ...) { ... }` closes `r` automatically, even if an exception happens. Use it for every stream. |
| `IOException` | The checked exception of file/network IO. We catch it and log, because a missing session file is not an error. |
| Gson + `TypeToken` | Gson needs to know `List<SessionItem>` at runtime, but Java erases generics. `new TypeToken<List<SessionItem>>(){}.getType()` is the standard workaround. |
| `Log` | `Log.w(TAG, "message", exception)` → shows in `adb logcat -s Inventario`. |
| static helper | `App.from(context)` is a **static method**: called on the class, no instance needed. |
| `synchronized` (again) | `SessionStore` is called from the UI and could be called from a callback; keep reads/writes serialized. |

## Files to create / change

### `data/SessionStore.java`

Package `com.pessimaideia.inventory.data` → folder `app/src/main/java/com/pessimaideia/inventory/data/`.

The store takes a `File` directory, **not** a `Context`, on purpose: tests can hand it a temporary
folder and no Android is involved.

```java
package com.pessimaideia.inventory.data;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.pessimaideia.inventory.model.SessionItem;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the in-progress scanning session as JSON so it survives the app being closed.
 * An empty session is represented by "no file".
 */
public class SessionStore {

    private static final String TAG = "Inventario";
    private static final String FILE_NAME = "session.json";
    private static final Type LIST_TYPE = new TypeToken<List<SessionItem>>() {}.getType();

    private final File file;
    private final Gson gson = new Gson();

    public SessionStore(File directory) {
        this.file = new File(directory, FILE_NAME);
    }

    /** True if there is an unsent session on disk. */
    public synchronized boolean hasSession() {
        return file.exists() && file.length() > 0;
    }

    /** Loads the session, or an empty list if there is none or the file is unreadable. */
    public synchronized List<SessionItem> load() {
        if (!hasSession()) return new ArrayList<>();
        try (Reader reader = new FileReader(file)) {
            List<SessionItem> items = gson.fromJson(reader, LIST_TYPE);
            return items != null ? items : new ArrayList<>();
        } catch (IOException | JsonParseException e) {
            Log.w(TAG, "Could not read session file, starting empty", e);
            return new ArrayList<>();
        }
    }

    /** Writes the whole session. An empty list removes the file. */
    public synchronized void save(List<SessionItem> items) {
        if (items.isEmpty()) {
            clear();
            return;
        }
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(items, LIST_TYPE, writer);
        } catch (IOException e) {
            Log.e(TAG, "Could not save session", e);
        }
    }

    /** Forgets the session (after Send or Cancel). */
    public synchronized void clear() {
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Could not delete session file");
        }
    }
}
```

Notes:
- `private static final` constants: `static` = one per class, `final` = never reassigned,
  ALL_CAPS by convention.
- `new TypeToken<List<SessionItem>>() {}` — that trailing `{}` is an anonymous subclass. It looks
  odd; it is Gson's documented idiom. Copy it as is.
- `catch (IOException | JsonParseException e)` catches either type in one block.
- `items != null ? items : new ArrayList<>()` is the ternary operator: `condition ? a : b`.
- The `Log` calls are Android. In JVM tests they would throw "not mocked"... **except** we will
  tell Gradle to make them no-ops (see the Gradle change below). That keeps the class simple.
- Gson can create `Product`/`SessionItem` even though they have `final` fields and no empty
  constructor. It uses reflection. This works on API 17.
- Disk IO on the main thread is normally forbidden. The session file is a few KB, so we accept it
  for now; the plan's Step 11 can move it to the executor if the CN51 stutters.

### Gradle: let unit tests call `Log`

In `app/build.gradle`, inside the `android { ... }` block, add:

```groovy
    testOptions {
        unitTests.returnDefaultValues = true
    }
```

Without this, any Android method called from a JVM test throws `RuntimeException: Method ... not
mocked`. With it, Android methods return `null`/`0`/nothing, which is exactly right for `Log`.

### `App.java`

Package `com.pessimaideia.inventory` (same as `MainActivity`).

```java
package com.pessimaideia.inventory;

import android.app.Application;
import android.content.Context;

import com.pessimaideia.inventory.api.InventoryApi;
import com.pessimaideia.inventory.api.MockInventoryApi;
import com.pessimaideia.inventory.data.SessionStore;

/** Process-wide objects. Android creates this once, before any Activity. */
public class App extends Application {

    public static final String TAG = "Inventario";

    private InventoryApi api;
    private SessionStore sessionStore;

    @Override
    public void onCreate() {
        super.onCreate();
        api = MockInventoryApi.forAndroid();      // Step 12: swap for HttpInventoryApi
        sessionStore = new SessionStore(getFilesDir());
    }

    public InventoryApi api() {
        return api;
    }

    public SessionStore sessionStore() {
        return sessionStore;
    }

    /** From any Activity: App.from(this).api() */
    public static App from(Context context) {
        return (App) context.getApplicationContext();
    }
}
```

`(App) context.getApplicationContext()` is a **cast**: Android gives back a generic `Context`; we
know it is our `App` because the manifest says so (next change).

### Manifest: register `App`

In `app/src/main/AndroidManifest.xml`, add `android:name=".App"` to the `<application>` tag:

```xml
    <application
        android:name=".App"
        android:allowBackup="true"
        ...
```

Forgetting this is the classic mistake: the app runs, but `App.from(this)` throws
`ClassCastException` because Android built a plain `Application`.

## Tests

File `app/src/test/java/com/pessimaideia/inventory/data/SessionStoreTest.java`.

```java
package com.pessimaideia.inventory.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.pessimaideia.inventory.model.Category;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.model.SessionItem;
import com.pessimaideia.inventory.model.Unit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class SessionStoreTest {

    /** JUnit creates a fresh temp directory per test and deletes it afterwards. */
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private SessionStore store;

    @Before
    public void setUp() {
        store = new SessionStore(temp.getRoot());
    }

    private static SessionItem sampleItem(int packages) {
        Product rice = new Product(1, "7896004000015", "Arroz 5kg",
                Category.KITCHEN, Unit.KG, 5, null);
        return new SessionItem(rice, packages);
    }

    @Test
    public void emptyWhenNothingSaved() {
        assertFalse(store.hasSession());
        assertTrue(store.load().isEmpty());
    }

    @Test
    public void saveThenLoadRoundTrips() {
        List<SessionItem> items = new ArrayList<>();
        items.add(sampleItem(3));
        store.save(items);

        assertTrue(store.hasSession());
        List<SessionItem> loaded = store.load();
        assertEquals(1, loaded.size());
        assertEquals(3, loaded.get(0).packages);
        assertEquals("Arroz 5kg", loaded.get(0).product.name);
        assertEquals(Unit.KG, loaded.get(0).product.unit);
        assertEquals(15.0, loaded.get(0).amount(), 0.0001);
    }

    @Test
    public void savingEmptyListClears() {
        List<SessionItem> items = new ArrayList<>();
        items.add(sampleItem(1));
        store.save(items);
        store.save(new ArrayList<>());

        assertFalse(store.hasSession());
    }

    @Test
    public void corruptFileLoadsAsEmpty() throws IOException {
        File file = new File(temp.getRoot(), "session.json");
        try (Writer w = new FileWriter(file)) {
            w.write("{ this is not json");
        }

        assertTrue(store.load().isEmpty());
    }
}
```

New JUnit bits: `@Rule TemporaryFolder` gives an isolated directory; `throws IOException` on a test
method lets checked exceptions propagate (a thrown exception simply fails the test).

## Verify

```sh
./gradlew test assembleDebug
```

Expected: 9 tests pass (5 from Step 2 + 4 here) and the APK builds. Then check the manifest change
really made it into the APK:

```sh
~/Android/Sdk/build-tools/36.0.0/aapt2 dump xmltree --file AndroidManifest.xml \
  app/build/outputs/apk/debug/app-debug.apk | grep -A3 "E: application" | grep name
```

You should see `android:name(...)="com.pessimaideia.inventory.App"`.

| Message | Meaning |
|---|---|
| `Method w in android.util.Log not mocked` | `testOptions { unitTests.returnDefaultValues = true }` is missing or in the wrong block. |
| `ClassCastException: android.app.Application cannot be cast to ...App` (on device, later) | `android:name=".App"` missing from the manifest. |
| `cannot find symbol: class TemporaryFolder` | The import `org.junit.rules.TemporaryFolder` is missing. |

## Checklist

- [ ] `data/SessionStore.java`
- [ ] `testOptions` block in `app/build.gradle`
- [ ] `App.java`
- [ ] `android:name=".App"` in the manifest
- [ ] `test/.../data/SessionStoreTest.java`
- [ ] `./gradlew test assembleDebug` → 9 tests, BUILD SUCCESSFUL
- [ ] commit: `git add -A && git commit -m "Step 3: App singleton and SessionStore"`
