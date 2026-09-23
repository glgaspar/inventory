# Step 2 — `InventoryApi` interface, callbacks, and the mock backend

Goal: define *how the UI talks to the backend* without having a backend. Every screen will call an
`InventoryApi`; today the only implementation is `MockInventoryApi` (in-memory, fake delay). In
Step 12 an `HttpInventoryApi` implements the same interface and nothing else changes.

Done when `./gradlew test` passes with the tests at the bottom of this file.

## Concepts you meet in this step

| Concept | One-liner |
|---|---|
| **interface** | A contract: method signatures with no bodies. Classes `implements` it. Lets the UI depend on "an API" without caring which one. |
| **generics** `<T>` | A type parameter. `ApiCallback<Product>` is a callback that delivers a `Product`; `ApiCallback<List<Movement>>` delivers a list. One class, many types. |
| **callback** | Instead of *returning* a value, the method takes an object with an `onSuccess` method and calls it later. Needed because the network answer arrives later, on another thread. |
| **anonymous class vs lambda** | Two ways to hand over a small piece of behavior. A lambda `() -> { ... }` works only when the interface has exactly one method. |
| **checked exception** | Java forces you to `catch` or `throws` some exceptions (e.g. `IOException`). We use one custom `ApiException`. |
| **Runnable / Executor** | `Runnable` = "something to run"; `Executor` = "something that runs Runnables", possibly on another thread. |
| **main thread** | Android UI can only be touched from the main thread. Background work must *post* results back to it. `Handler(Looper.getMainLooper())` does that. |
| **synchronized** | Only one thread at a time may run this method. Protects the mock's lists. |
| **static** | Belongs to the class, not to an instance. `MockInventoryApi.forAndroid()` is a static factory. |

## Why the "poster" indirection

`Handler` is an Android class; in a desktop JUnit test it throws "Method not mocked". So the mock
never touches `Handler` directly. It receives a tiny `MainThreadPoster` interface: on Android the
real one uses a `Handler`; in tests a "direct" one just runs the code immediately. Same trick for the
background `Executor`: tests pass `Runnable::run`, which runs inline. Result: tests are synchronous
and need no threads.

## Files to create

Package `com.pessimaideia.inventory.api` → folder `app/src/main/java/com/pessimaideia/inventory/api/`.

### `ApiException.java`

```java
package com.pessimaideia.inventory.api;

/** Any failure talking to the backend. statusCode is the HTTP status, or 0 for "no response". */
public class ApiException extends Exception {

    public final int statusCode;

    public ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public boolean isNotFound() {
        return statusCode == 404;
    }
}
```

`extends Exception` makes it a *checked* exception: any method that throws it must say `throws
ApiException`, and callers must handle it. `super(message)` calls the parent constructor.

### `ApiCallback.java`

```java
package com.pessimaideia.inventory.api;

/** Receives the result of an asynchronous API call. Both methods run on the main thread. */
public interface ApiCallback<T> {
    void onSuccess(T result);
    void onError(ApiException error);
}
```

### `MainThreadPoster.java`

```java
package com.pessimaideia.inventory.api;

/** Runs a Runnable on the thread that owns the UI. */
public interface MainThreadPoster {

    void post(Runnable runnable);

    /** For tests: runs immediately on the calling thread. */
    MainThreadPoster DIRECT = new MainThreadPoster() {
        @Override
        public void post(Runnable runnable) {
            runnable.run();
        }
    };
}
```

`DIRECT` is an **anonymous class**: an object created from the interface on the spot, with the
method body written inline. Fields declared in an interface are automatically `public static final`.

### `AndroidMainThreadPoster.java`

```java
package com.pessimaideia.inventory.api;

import android.os.Handler;
import android.os.Looper;

/** Posts to Android's main (UI) thread. */
public class AndroidMainThreadPoster implements MainThreadPoster {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void post(Runnable runnable) {
        handler.post(runnable);
    }
}
```

This is the *only* file in the package that imports Android. Keep it that way.

### `MovementBatch.java`  (in the **model** package)

What the app sends when a session is submitted. Mirrors `MovementBatch` in `docs/API.md`.

```java
package com.pessimaideia.inventory.model;

import java.util.List;

/** Body of POST /movements. */
public class MovementBatch {

    public static class Item {
        public final long productId;
        public final int packages;
        public final double amount;

        public Item(long productId, int packages, double amount) {
            this.productId = productId;
            this.packages = packages;
            this.amount = amount;
        }
    }

    public final Movement.Type type;
    public final String at;
    public final List<Item> items;

    public MovementBatch(Movement.Type type, String at, List<Item> items) {
        this.type = type;
        this.at = at;
        this.items = items;
    }
}
```

`static class Item` nested inside: a class that only makes sense as part of a batch. From outside
it is `MovementBatch.Item`. `List<Item>` is an interface from `java.util`; the concrete object will
usually be an `ArrayList`.

### `InventoryApi.java`

```java
package com.pessimaideia.inventory.api;

import com.pessimaideia.inventory.model.InventoryItem;
import com.pessimaideia.inventory.model.Movement;
import com.pessimaideia.inventory.model.MovementBatch;
import com.pessimaideia.inventory.model.Product;

import java.util.List;

/**
 * Everything the app needs from the backend. One method per endpoint in docs/API.md.
 * All methods return immediately and deliver the result through the callback on the main thread.
 */
public interface InventoryApi {

    /** GET /products/by-barcode/{barcode} — 404 → onError with isNotFound(). */
    void getProductByBarcode(String barcode, ApiCallback<Product> callback);

    /** POST /products — product.id is ignored; the returned Product has the real id. */
    void createProduct(Product product, ApiCallback<Product> callback);

    /** POST /movements — delivers the id of the created batch. */
    void sendMovements(MovementBatch batch, ApiCallback<Long> callback);

    /** GET /inventory */
    void getInventory(ApiCallback<List<InventoryItem>> callback);

    /** GET /products/{id} */
    void getProduct(long id, ApiCallback<Product> callback);

    /** GET /products/{id}/movements — newest first. */
    void getMovements(long productId, ApiCallback<List<Movement>> callback);
}
```

`ApiCallback<Long>` not `ApiCallback<long>`: generics only accept object types. `Long` is the
boxed version of `long`; Java converts between them automatically (autoboxing).

### `MockInventoryApi.java`

Read this one slowly; it is the longest file so far. Sections are marked.

```java
package com.pessimaideia.inventory.api;

import com.pessimaideia.inventory.model.Category;
import com.pessimaideia.inventory.model.InventoryItem;
import com.pessimaideia.inventory.model.Movement;
import com.pessimaideia.inventory.model.MovementBatch;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.model.Unit;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** In-memory backend with a fake network delay. Data resets every time the app starts. */
public class MockInventoryApi implements InventoryApi {

    // ---- construction --------------------------------------------------------------------

    private final Executor background;
    private final MainThreadPoster mainThread;
    private final long delayMs;

    public MockInventoryApi(Executor background, MainThreadPoster mainThread, long delayMs) {
        this.background = background;
        this.mainThread = mainThread;
        this.delayMs = delayMs;
        seed();
    }

    /** What the app uses: one background thread, real main thread, 300 ms fake latency. */
    public static MockInventoryApi forAndroid() {
        return new MockInventoryApi(Executors.newSingleThreadExecutor(),
                new AndroidMainThreadPoster(), 300);
    }

    /** What tests use: everything runs inline on the calling thread, no delay. */
    public static MockInventoryApi synchronous() {
        return new MockInventoryApi(new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        }, MainThreadPoster.DIRECT, 0);
    }

    // ---- fake database -------------------------------------------------------------------

    private final List<Product> products = new ArrayList<>();
    private final List<Movement> movements = new ArrayList<>();
    private long nextProductId = 1;
    private long nextMovementId = 1;
    private long nextBatchId = 1;

    private void seed() {
        addProduct("7896004000015", "Arroz 5kg", Category.KITCHEN, Unit.KG, 5);
        addProduct("7896004000022", "Feijão 1kg", Category.KITCHEN, Unit.KG, 1);
        addProduct("7896004000039", "Óleo de soja 900mL", Category.KITCHEN, Unit.ML, 900);
        addProduct("7896004000046", "Café 500g", Category.KITCHEN, Unit.G, 500);
        addProduct("7896004000053", "Detergente 500mL", Category.CLEANING, Unit.ML, 500);
        addProduct("7896004000060", "Sabão em pó 1kg", Category.CLEANING, Unit.KG, 1);
        addProduct("7896004000077", "Papel higiênico 12 rolos", Category.BATHROOM, Unit.UN, 12);
        addProduct("7896004000084", "Sabonete", Category.BATHROOM, Unit.UN, 1);

        // Some history so the inventory and detail screens have something to show.
        addMovement(1, Movement.Type.IN, 2, "2026-09-01T10:00:00Z");
        addMovement(1, Movement.Type.OUT, 1, "2026-09-10T19:30:00Z");
        addMovement(2, Movement.Type.IN, 3, "2026-09-01T10:00:00Z");
        addMovement(5, Movement.Type.IN, 2, "2026-09-05T15:00:00Z");
        addMovement(7, Movement.Type.IN, 1, "2026-09-05T15:00:00Z");
    }

    private synchronized Product addProduct(String barcode, String name, Category category,
                                            Unit unit, double packageSize) {
        Product p = new Product(nextProductId++, barcode, name, category, unit, packageSize, null);
        products.add(p);
        return p;
    }

    private synchronized Movement addMovement(long productId, Movement.Type type,
                                              int packages, String at) {
        Product p = findProductById(productId);
        Movement m = new Movement(nextMovementId++, productId, type,
                packages * p.packageSize, packages, at);
        movements.add(m);
        return m;
    }

    private synchronized Product findProductById(long id) {
        for (Product p : products) {
            if (p.id == id) return p;
        }
        return null;
    }

    private synchronized Product findProductByBarcode(String barcode) {
        for (Product p : products) {
            if (p.barcode.equals(barcode)) return p;
        }
        return null;
    }

    // ---- the InventoryApi methods ----------------------------------------------------------

    @Override
    public void getProductByBarcode(final String barcode, ApiCallback<Product> callback) {
        run(callback, new Callable<Product>() {
            @Override
            public Product call() throws ApiException {
                Product p = findProductByBarcode(barcode);
                if (p == null) throw new ApiException(404, "Unknown barcode " + barcode);
                return p;
            }
        });
    }

    @Override
    public void createProduct(final Product product, ApiCallback<Product> callback) {
        run(callback, () -> {
            if (findProductByBarcode(product.barcode) != null) {
                throw new ApiException(409, "Barcode already registered");
            }
            return addProduct(product.barcode, product.name, product.category,
                    product.unit, product.packageSize);
        });
    }

    @Override
    public void sendMovements(final MovementBatch batch, ApiCallback<Long> callback) {
        run(callback, () -> {
            for (MovementBatch.Item item : batch.items) {
                if (findProductById(item.productId) == null) {
                    throw new ApiException(400, "Unknown product " + item.productId);
                }
            }
            for (MovementBatch.Item item : batch.items) {
                addMovement(item.productId, batch.type, item.packages, batch.at);
            }
            return nextBatchId++;
        });
    }

    @Override
    public void getInventory(ApiCallback<List<InventoryItem>> callback) {
        run(callback, () -> {
            List<InventoryItem> result = new ArrayList<>();
            synchronized (this) {
                for (Product p : products) {
                    double amount = 0;
                    String updatedAt = null;
                    for (Movement m : movements) {
                        if (m.productId != p.id) continue;
                        amount += (m.type == Movement.Type.IN) ? m.amount : -m.amount;
                        if (updatedAt == null || m.at.compareTo(updatedAt) > 0) updatedAt = m.at;
                    }
                    if (updatedAt == null) continue;   // never moved → not in inventory
                    int packages = (int) Math.floor(amount / p.packageSize);
                    result.add(new InventoryItem(p, amount, packages, updatedAt));
                }
            }
            return result;
        });
    }

    @Override
    public void getProduct(final long id, ApiCallback<Product> callback) {
        run(callback, () -> {
            Product p = findProductById(id);
            if (p == null) throw new ApiException(404, "Unknown product " + id);
            return p;
        });
    }

    @Override
    public void getMovements(final long productId, ApiCallback<List<Movement>> callback) {
        run(callback, () -> {
            List<Movement> result = new ArrayList<>();
            synchronized (this) {
                for (Movement m : movements) {
                    if (m.productId == productId) result.add(m);
                }
            }
            // newest first: ISO-8601 strings sort correctly as plain text
            Collections.sort(result, (a, b) -> b.at.compareTo(a.at));
            return result;
        });
    }

    // ---- plumbing ---------------------------------------------------------------------------

    /**
     * Runs `work` on the background executor, waits the fake delay, then delivers the result
     * (or the ApiException) to the callback on the main thread.
     */
    private <T> void run(final ApiCallback<T> callback, final Callable<T> work) {
        background.execute(() -> {
            try {
                if (delayMs > 0) Thread.sleep(delayMs);
                final T result = work.call();
                mainThread.post(() -> callback.onSuccess(result));
            } catch (final ApiException e) {
                mainThread.post(() -> callback.onError(e));
            } catch (final Exception e) {
                mainThread.post(() -> callback.onError(new ApiException(0, e.toString())));
            }
        });
    }

    /** Current time as the backend would format it. */
    public static String nowIso() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
}
```

Things worth noticing:

- `getProductByBarcode` uses an **anonymous class** for `Callable`; every other method uses a
  **lambda** `() -> { ... }` for the same thing. Same meaning, lambda is shorter. Use lambdas.
- `private <T> void run(...)`: a **generic method**. `T` is decided per call, so one `run` serves
  `Product`, `Long`, `List<Movement>`...
- `final` on parameters used inside a lambda/anonymous class: Java requires captured variables to
  be effectively final. Writing `final` explicitly is optional but makes the rule visible.
- `for (Product p : products)`: the enhanced for loop, "for each product in products".
- `synchronized (this) { ... }` block vs `synchronized` method: same lock, block form when only part
  of the method needs it.
- `(int) Math.floor(x)`: a **cast**, converting `double` to `int` explicitly.
- `m.type == Movement.Type.IN`: enums are compared with `==` (there is exactly one instance).
  Strings are compared with `.equals()`, never `==`.
- `Collections.sort(list, comparator)` with a lambda `(a, b) -> ...` that returns negative /
  zero / positive. `b.compareTo(a)` reverses the order.
- No `java.time`, no streams: those need API 26 without desugaring. `SimpleDateFormat` is the
  API 17 tool for dates.

## Tests

Folder `app/src/test/java/com/pessimaideia/inventory/api/`. You can delete the template's
`app/src/test/java/com/pessimaideia/inventory/ExampleUnitTest.java`.

### `RecordingCallback.java` (test helper)

```java
package com.pessimaideia.inventory.api;

/** Test helper: remembers whatever the API delivered so the test can assert on it. */
public class RecordingCallback<T> implements ApiCallback<T> {

    public T result;
    public ApiException error;

    @Override
    public void onSuccess(T result) {
        this.result = result;
    }

    @Override
    public void onError(ApiException error) {
        this.error = error;
    }
}
```

### `MockInventoryApiTest.java`

```java
package com.pessimaideia.inventory.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.pessimaideia.inventory.model.Category;
import com.pessimaideia.inventory.model.InventoryItem;
import com.pessimaideia.inventory.model.Movement;
import com.pessimaideia.inventory.model.MovementBatch;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.model.Unit;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MockInventoryApiTest {

    private MockInventoryApi api;

    @Before
    public void setUp() {
        api = MockInventoryApi.synchronous();   // fresh data before every test
    }

    @Test
    public void knownBarcodeReturnsProduct() {
        RecordingCallback<Product> cb = new RecordingCallback<>();
        api.getProductByBarcode("7896004000015", cb);

        assertNull(cb.error);
        assertNotNull(cb.result);
        assertEquals("Arroz 5kg", cb.result.name);
        assertEquals(Unit.KG, cb.result.unit);
    }

    @Test
    public void unknownBarcodeIs404() {
        RecordingCallback<Product> cb = new RecordingCallback<>();
        api.getProductByBarcode("0000000000000", cb);

        assertNull(cb.result);
        assertNotNull(cb.error);
        assertTrue(cb.error.isNotFound());
    }

    @Test
    public void createdProductCanBeFoundByBarcode() {
        Product draft = new Product(0, "1234567890123", "Vinagre 750mL",
                Category.KITCHEN, Unit.ML, 750, null);
        RecordingCallback<Product> created = new RecordingCallback<>();
        api.createProduct(draft, created);

        assertNull(created.error);
        assertTrue(created.result.id > 0);

        RecordingCallback<Product> found = new RecordingCallback<>();
        api.getProductByBarcode("1234567890123", found);
        assertEquals(created.result.id, found.result.id);
    }

    @Test
    public void sendingMovementsUpdatesInventory() {
        List<MovementBatch.Item> items = new ArrayList<>();
        items.add(new MovementBatch.Item(2, 2, 2.0));        // Feijão 1kg × 2
        MovementBatch batch = new MovementBatch(Movement.Type.IN, "2026-09-22T12:00:00Z", items);

        RecordingCallback<Long> sent = new RecordingCallback<>();
        api.sendMovements(batch, sent);
        assertNull(sent.error);

        RecordingCallback<List<InventoryItem>> inv = new RecordingCallback<>();
        api.getInventory(inv);
        InventoryItem feijao = null;
        for (InventoryItem item : inv.result) {
            if (item.product.id == 2) feijao = item;
        }
        assertNotNull(feijao);
        assertEquals(5.0, feijao.amount, 0.0001);            // seeded 3 + 2 sent
        assertEquals(5, feijao.packages);
        assertEquals("2026-09-22T12:00:00Z", feijao.updatedAt);
    }

    @Test
    public void movementsComeNewestFirst() {
        RecordingCallback<List<Movement>> cb = new RecordingCallback<>();
        api.getMovements(1, cb);

        assertEquals(2, cb.result.size());
        assertEquals(Movement.Type.OUT, cb.result.get(0).type);
        assertEquals(Movement.Type.IN, cb.result.get(1).type);
    }
}
```

JUnit vocabulary: `@Before` runs before each `@Test`; `assertEquals(expected, actual)`; for doubles
a third argument is the tolerance. `import static` lets you write `assertEquals` instead of
`Assert.assertEquals`.

## Verify

```sh
./gradlew test
```

Expected: `BUILD SUCCESSFUL` and no "FAILED" lines. To see the test names:

```sh
./gradlew test --console=plain 2>&1 | grep -E "Test|PASSED|FAILED|BUILD"
```

The HTML report is at `app/build/reports/tests/testDebugUnitTest/index.html`.

Common errors:

| Message | Meaning |
|---|---|
| `incompatible types: bad return type in lambda expression` | The lambda returns something that is not `T`; check the method's `ApiCallback<...>` type. |
| `local variables referenced from a lambda expression must be final or effectively final` | You reassigned a variable that a lambda uses. Introduce a new variable instead. |
| `unreported exception ApiException; must be caught or declared to be thrown` | You called something that throws it outside `run(...)`. |
| `Method ... not mocked` at test time | Android code (e.g. `Handler`) ran in a JVM test. Only `AndroidMainThreadPoster` may touch Android. |

## Checklist

- [ ] `api/ApiException.java`
- [ ] `api/ApiCallback.java`
- [ ] `api/MainThreadPoster.java`
- [ ] `api/AndroidMainThreadPoster.java`
- [ ] `model/MovementBatch.java`
- [ ] `api/InventoryApi.java`
- [ ] `api/MockInventoryApi.java`
- [ ] `test/.../api/RecordingCallback.java`
- [ ] `test/.../api/MockInventoryApiTest.java` (delete `ExampleUnitTest.java`)
- [ ] `./gradlew test` → BUILD SUCCESSFUL, 5 tests pass
- [ ] commit: `git add -A && git commit -m "Step 2: InventoryApi and in-memory mock"`
