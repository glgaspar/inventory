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