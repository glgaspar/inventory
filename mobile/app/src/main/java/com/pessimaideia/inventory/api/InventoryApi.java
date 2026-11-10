package com.pessimaideia.inventory.api;
import com.pessimaideia.inventory.model.InventoryItem;
import com.pessimaideia.inventory.model.Movement;
import com.pessimaideia.inventory.model.MovementBatch;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.model.RecipeResult;
import com.pessimaideia.inventory.model.Suggestion;
import java.util.List;

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

    void lookup(String barcode, ApiCallback<Suggestion> callback);

    void suggestRecipes(String hint, String lang, boolean refresh, ApiCallback<RecipeResult> callback);
}