# Step 8 — Send / Cancel the session

Goal: the Scanning screen gets two actions in its title bar. **Send** posts everything in the
list as one entry (`POST /movements`), clears the session and goes back home. **Cancel session**
asks for confirmation, clears the session and goes back home. If sending fails, a bar at the
bottom says why and offers **Retry**; the session stays on disk, so nothing is lost.

Done when sending two products returns you to the home screen with no Continue button, and a
failing send shows *Could not send: …* with *Retry* while the list stays as it was.

Notes:
- **Send is greyed out** while the list is empty or a request is running.
- **The busy flag now covers sending too**, so `lookingUp` becomes `busy`, and the "still looking
  up" text becomes a general "please wait".
- `MockInventoryApi` keeps new products **only in memory**. That gives you a free failure test:
  register a product, force-stop the app, reopen the session, and Send fails with
  `Unknown product 9` (the mock forgot it). With the real backend (Step 12) products persist, so
  this particular failure goes away; the Retry path stays for network errors.
- **Where is Cancel session?** Actions that do not fit in the title bar go to the overflow
  menu. On a device with a hardware **Menu** key (this emulator; check the CN51), Android shows no
  `⋮` button: press the Menu key instead (`adb shell input keyevent 82`, or the emulator's menu
  button).

## Concepts you meet in this step

| Concept | One-liner |
|---|---|
| Options menu | The title bar's actions. Declared in `res/menu/*.xml`, inflated in `onCreateOptionsMenu`, clicks arrive in `onOptionsItemSelected`. |
| `app:showAsAction` | `always` = in the title bar; `never` = in the overflow menu; `withText` = show the title, not just an icon. `app:` (not `android:`) because AppCompat draws the bar on API 17. |
| `onPrepareOptionsMenu` + `invalidateOptionsMenu()` | `onPrepare…` runs before the menu is shown; `invalidateOptionsMenu()` asks Android to run it again. That's how Send turns grey or active as the list changes. |
| `finish()` | Closes the current Activity. The one below it (home) comes back and its `onResume` runs, so the Continue button updates by itself. |
| `Snackbar` | A bar at the bottom with a message and an optional action button. `LENGTH_INDEFINITE` keeps it until the user acts or it's replaced. From the Material library already in the project. |
| `SessionActivity.this` | Inside an anonymous class, `this` is the anonymous object (the `ApiCallback`). `SessionActivity.this` reaches the Activity around it, which `Toast.makeText` needs as its `Context`. |
| `Long` in `ApiCallback<Long>` | Generics take objects, not primitives, so the batch id is a `Long` (object), not a `long`. |
| `SimpleDateFormat` + `Locale.US` + UTC | Formats a `Date` as `2026-09-22T13:05:00Z`. `Locale.US` keeps the digits ASCII whatever the device language; UTC because the API stores UTC (docs/API.md). |
| Rename refactoring | In Android Studio, put the cursor on `lookingUp`, **Shift+F6**, type `busy`, Enter: every use is renamed. Same for `setLookingUp` → `setBusy`. |
| Package-private | `static String isoUtc(...)` without `public`/`private`: visible only inside `com.pessimaideia.inventory.model`. Tests in the same package (under `src/test`) can call it; the rest of the app cannot. |

## Files

### `res/values/strings.xml` — change `scan_busy`, add

Change the text of the existing `scan_busy`:

```xml
    <string name="scan_busy">Please wait, still working</string>
```

Add:

```xml
    <string name="action_send">Send</string>
    <string name="action_cancel_session">Cancel session</string>
    <plurals name="sent_toast">
        <item quantity="one">%d item sent</item>
        <item quantity="other">%d items sent</item>
    </plurals>
    <string name="send_failed">Could not send: %1$s</string>
    <string name="retry">Retry</string>
```

### `res/values-pt-rBR/strings.xml` — change `scan_busy`, add

```xml
    <string name="scan_busy">Aguarde, ainda processando</string>
```

```xml
    <string name="action_send">Enviar</string>
    <string name="action_cancel_session">Cancelar entrada</string>
    <plurals name="sent_toast">
        <item quantity="one">%d item enviado</item>
        <item quantity="other">%d itens enviados</item>
    </plurals>
    <string name="send_failed">Falha ao enviar: %1$s</string>
    <string name="retry">Tentar de novo</string>
```

The Cancel confirmation reuses Step 4's `discard_session_title` / `discard_session_message` /
`discard` / `cancel`: it is the same question the home screen asks.

### `model/MovementBatch.java` — add a factory

Replace the single `import java.util.List;` with:

```java
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
```

Add these two methods at the end of the class, before its closing `}`:

```java
    /** An entry ("in") of everything in a scanning session, stamped with the given time. */
    public static MovementBatch entryFrom(List<SessionItem> session, Date now) {
        List<Item> items = new ArrayList<>();
        for (SessionItem sessionItem : session) {
            items.add(new Item(sessionItem.product.id, sessionItem.packages, sessionItem.amount()));
        }
        return new MovementBatch(Movement.Type.IN, isoUtc(now), items);
    }

    /** 2026-09-22T13:05:00Z, the format docs/API.md uses. */
    static String isoUtc(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(date);
    }
```

Why here and not in the Activity: turning a session into a request body is plain data work, so
it can be unit tested on the desktop, and `HttpInventoryApi` (Step 12) will send exactly this
object. `MockInventoryApi.nowIso()` does the same formatting for the mock's own timestamps; the
app should not depend on the mock, hence the second copy here.

### `test/.../model/MovementBatchTest.java` — new

New folder `app/src/test/java/com/pessimaideia/inventory/model/`.

```java
package com.pessimaideia.inventory.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MovementBatchTest {

    private static final Product RICE =
            new Product(1, "7896004000015", "Arroz 5kg", Category.KITCHEN, Unit.KG, 5, null);
    private static final Product SOAP =
            new Product(8, "7896004000084", "Sabonete", Category.BATHROOM, Unit.UN, 1, null);

    @Test
    public void entryHasOneItemPerSessionRow() {
        List<SessionItem> session = Arrays.asList(new SessionItem(RICE, 2), new SessionItem(SOAP, 3));

        MovementBatch batch = MovementBatch.entryFrom(session, new Date(0));

        assertEquals(Movement.Type.IN, batch.type);
        assertEquals(2, batch.items.size());
        assertEquals(1, batch.items.get(0).productId);
        assertEquals(2, batch.items.get(0).packages);
        assertEquals(10.0, batch.items.get(0).amount, 0.0);
        assertEquals(8, batch.items.get(1).productId);
        assertEquals(3.0, batch.items.get(1).amount, 0.0);
    }

    @Test
    public void timestampIsUtcIso8601() {
        // Date(0) is the Unix epoch; 90061000 ms later is 1 day, 1 h, 1 min, 1 s.
        assertEquals("1970-01-01T00:00:00Z", MovementBatch.isoUtc(new Date(0)));
        assertEquals("1970-01-02T01:01:01Z", MovementBatch.isoUtc(new Date(90061000L)));
    }
}
```

`Arrays.asList(a, b)` builds a small fixed-size list, handy in tests. `./gradlew test` →
**17 tests**.

### `res/menu/session.xml` — new

New folder `app/src/main/res/menu/` (in Android Studio: right-click `res` → New → Android
Resource Directory → type *menu*).

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <item
        android:id="@+id/action_send"
        android:title="@string/action_send"
        app:showAsAction="always|withText" />

    <item
        android:id="@+id/action_cancel_session"
        android:title="@string/action_cancel_session"
        app:showAsAction="never" />
</menu>
```

### `ui/session/SessionActivity.java` — replace

The full file, since the changes are spread out. What changed compared to Step 7:

1. `lookingUp` → `busy`, `setLookingUp` → `setBusy` (Shift+F6, see above), and its comment.
2. New imports: `Menu`, `MenuItem`, `AlertDialog`, `Snackbar`, `MovementBatch`, `Date`.
3. A new section *toolbar: Send / Cancel session* with `onCreateOptionsMenu`,
   `onPrepareOptionsMenu`, `onOptionsItemSelected`, `send()`, `confirmCancel()`.
4. `invalidateOptionsMenu()` at the end of `setBusy(...)` and of `save()`.

```java
package com.pessimaideia.inventory.ui.session;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.pessimaideia.inventory.App;
import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.api.ApiCallback;
import com.pessimaideia.inventory.api.ApiException;
import com.pessimaideia.inventory.api.InventoryApi;
import com.pessimaideia.inventory.data.SessionStore;
import com.pessimaideia.inventory.model.MovementBatch;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.model.SessionItem;
import com.pessimaideia.inventory.scanner.ScannerInput;
import com.pessimaideia.inventory.scanner.WedgeScannerInput;

import java.util.Date;
import java.util.List;

public class SessionActivity extends AppCompatActivity
        implements SessionAdapter.Listener, NewProductDialog.Listener {

    private final ScannerInput scanner = new WedgeScannerInput();

    private InventoryApi api;
    private SessionStore store;
    private List<SessionItem> items;
    private SessionAdapter adapter;

    private RecyclerView list;
    private ProgressBar progress;
    private TextView empty;

    /** True while waiting for the API (lookup, new product or send); scans meanwhile are refused. */
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);
        setTitle(R.string.title_session);

        App app = App.from(this);
        api = app.api();
        store = app.sessionStore();
        items = store.load();

        list = findViewById(R.id.list);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty);

        adapter = new SessionAdapter(items, this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        list.setAdapter(adapter);
        updateEmptyState();

        scanner.attach(this, this::onScan);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scanner.requestFocus();
    }

    // ---- toolbar: Send / Cancel session ---------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.session, menu);
        return true;
    }

    /** Runs before the menu is drawn, and again after every invalidateOptionsMenu(). */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_send).setEnabled(!items.isEmpty() && !busy);
        menu.findItem(R.id.action_cancel_session).setEnabled(!busy);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_send) {
            send();
            return true;
        }
        if (id == R.id.action_cancel_session) {
            confirmCancel();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void send() {
        final int count = items.size();
        MovementBatch batch = MovementBatch.entryFrom(items, new Date());
        setBusy(true);
        api.sendMovements(batch, new ApiCallback<Long>() {
            @Override
            public void onSuccess(Long batchId) {
                if (isDestroyed()) return;
                setBusy(false);
                store.clear();
                Toast.makeText(SessionActivity.this, getResources()
                        .getQuantityString(R.plurals.sent_toast, count, count), Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(ApiException error) {
                if (isDestroyed()) return;
                setBusy(false);
                // The session is still on disk: nothing is lost, the user can retry or edit.
                Snackbar.make(list, getString(R.string.send_failed, error.getMessage()),
                                Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.retry, v -> send())
                        .show();
            }
        });
    }

    private void confirmCancel() {
        if (items.isEmpty()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.discard_session_title)
                .setMessage(R.string.discard_session_message)
                .setPositiveButton(R.string.discard, (dialog, which) -> {
                    store.clear();
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---- scanning ------------------------------------------------------------------------

    private void onScan(String barcode) {
        if (busy) {
            toast(getString(R.string.scan_busy));
            return;
        }
        int position = indexOf(barcode);
        if (position >= 0) {
            // Already in the session: no need to ask the API again.
            onChangePackages(position, +1);
            return;
        }
        setBusy(true);
        api.getProductByBarcode(barcode, new ApiCallback<Product>() {
            @Override
            public void onSuccess(Product product) {
                if (isDestroyed()) return;
                setBusy(false);
                addItem(product);
            }

            @Override
            public void onError(ApiException error) {
                if (isDestroyed()) return;
                setBusy(false);
                if (error.isNotFound()) {
                    askForNewProduct(barcode);
                } else {
                    toast(getString(R.string.scan_failed, error.getMessage()));
                }
            }
        });
    }

    private void askForNewProduct(String barcode) {
        // If the user left the screen during the lookup, showing a dialog now would crash.
        if (getSupportFragmentManager().isStateSaved()) return;
        NewProductDialog.newInstance(barcode).show(getSupportFragmentManager(), "new_product");
    }

    @Override
    public void onNewProduct(Product draft) {
        setBusy(true);
        api.createProduct(draft, new ApiCallback<Product>() {
            @Override
            public void onSuccess(Product product) {
                if (isDestroyed()) return;
                setBusy(false);
                addItem(product);
            }

            @Override
            public void onError(ApiException error) {
                if (isDestroyed()) return;
                setBusy(false);
                toast(getString(R.string.create_failed, error.getMessage()));
            }
        });
    }

    private int indexOf(String barcode) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).product.barcode.equals(barcode)) return i;
        }
        return -1;
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.INVISIBLE);
        invalidateOptionsMenu();
    }

    // ---- changing the list ---------------------------------------------------------------

    private void addItem(Product product) {
        items.add(new SessionItem(product, 1));
        int position = items.size() - 1;
        adapter.notifyItemInserted(position);
        list.scrollToPosition(position);
        save();
    }

    @Override
    public void onChangePackages(int position, int delta) {
        SessionItem item = items.get(position);
        if (item.packages + delta < 1) return;
        item.packages += delta;
        adapter.notifyItemChanged(position);
        list.scrollToPosition(position);
        save();
    }

    @Override
    public void onRemove(int position) {
        items.remove(position);
        adapter.notifyItemRemoved(position);
        save();
    }

    private void save() {
        store.save(items);
        updateEmptyState();
        invalidateOptionsMenu();
    }

    private void updateEmptyState() {
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
```

- `send()` stores `count` **before** sending: `store.clear()` in `onSuccess` does not touch the
  `items` list, but reading the count up front makes the toast independent of what happens next.
- `v -> send()` in the Snackbar: *Retry* simply runs `send()` again, with a fresh timestamp.
- On failure nothing is cleared. The user can retry, edit the list, or leave; the session is
  still in `session.json`.
- `return super.onOptionsItemSelected(item);` for anything else, e.g. the Up arrow, so Android's
  default handling still works.

## Verify

`./gradlew test` (17 passing), then `./gradlew installDebug`. Open the app from the home screen
(not with `am start` straight into the session, or `finish()` has no home screen to return to).

1. **New session** → **Send** is in the title bar, greyed out (empty list).
2. Scan `7896004000015` and `7896004000084` → **Send** becomes active.
3. **Send** → the progress bar flashes, toast *2 items sent*, back on the home screen, **no**
   Continue button.
4. **New session**, scan `7896004000022` twice → Back → *Continue session (1 item)* → open it.
5. Press the **Menu** key (emulator side bar, or `adb shell input keyevent 82`) → *Cancel
   session* → dialog *Discard current session?* → **Cancel** → nothing changes.
6. Menu → *Cancel session* → **Discard** → home, no Continue button.
7. Failure path: **New session**, scan `1111`, register it (any name, amount `2`), scan
   `7896004000015`. Then `adb shell am force-stop com.pessimaideia.inventory`, reopen the app,
   **Continue**, **Send** → bar at the bottom *Could not send: Unknown product 9* with *Retry*;
   the list is unchanged. *Retry* fails the same way (the mock still doesn't know product 9).
   Remove the `1111` row with its trash button, tap *Retry* → sent, back home.
8. `adb logcat -s Inventory AndroidRuntime` → no stack traces.

| Symptom | Cause |
|---|---|
| No Send in the title bar | `onCreateOptionsMenu` missing, or it returns `false`. |
| Send shows as `⋮` / only in the menu | `android:showAsAction` instead of `app:showAsAction` (AppCompat ignores the `android:` one). |
| Send never turns grey / never turns active | `invalidateOptionsMenu()` missing in `setBusy` or `save()`, or `onPrepareOptionsMenu` not overridden. |
| `NullPointerException` in `onPrepareOptionsMenu` | `R.id.action_send` / `action_cancel_session` in Java doesn't match the `@+id` in `menu/session.xml`. |
| `error: incompatible types: <anonymous ApiCallback> cannot be converted to Context` | `Toast.makeText(this, ...)` inside the callback; use `SessionActivity.this`. |
| Continue button still there after Send | `store.clear()` missing in `onSuccess`. |
| After a failed send the list is gone | `store.clear()` or `finish()` in `onError`; failure must leave everything in place. |
| `IllegalArgumentException: No suitable parent found` (Snackbar) | The view passed to `Snackbar.make` is not inside the Activity's layout; use `list`. |

## Checklist

- [ ] strings (both languages): `scan_busy` text changed, 5 new entries
- [ ] `MovementBatch.entryFrom` + `isoUtc`, `MovementBatchTest`, `./gradlew test` → 17 passing
- [ ] `res/menu/session.xml`
- [ ] `SessionActivity`: `busy` rename, menu section, `invalidateOptionsMenu()` ×2
- [ ] Verify 1–8 on the emulator
- [ ] commit: `git add -A && git commit -m "Step 8: send and cancel the scanning session"`
