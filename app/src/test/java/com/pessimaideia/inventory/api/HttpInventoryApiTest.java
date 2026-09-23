package com.pessimaideia.inventory.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pessimaideia.inventory.model.Category;
import com.pessimaideia.inventory.model.InventoryItem;
import com.pessimaideia.inventory.model.Movement;
import com.pessimaideia.inventory.model.MovementBatch;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.model.Unit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class HttpInventoryApiTest {

    private static final String RICE = "{\"id\":1,\"barcode\":\"7896004000015\",\"name\":\"Arroz 5kg\","
            + "\"category\":\"kitchen\",\"unit\":\"kg\",\"packageSize\":5,\"imageUrl\":null}";

    private MockWebServer server;
    private HttpInventoryApi api;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        api = apiFor(server.url("/api/").toString());
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private static HttpInventoryApi apiFor(String baseUrl) {
        return new HttpInventoryApi(HttpInventoryApi.parseBaseUrl(baseUrl), new OkHttpClient(),
                Runnable::run, MainThreadPoster.DIRECT);
    }

    @Test
    public void productByBarcode() throws Exception {
        server.enqueue(new MockResponse().setBody(RICE));
        RecordingCallback<Product> cb = new RecordingCallback<>();

        api.getProductByBarcode("7896004000015", cb);

        assertNull(cb.error);
        assertEquals("Arroz 5kg", cb.result.name);
        assertEquals(Category.KITCHEN, cb.result.category);
        assertEquals(Unit.KG, cb.result.unit);
        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/api/products/by-barcode/7896004000015", request.getPath());
    }

    @Test
    public void unknownBarcodeIsNotFound() {
        server.enqueue(new MockResponse().setResponseCode(404));
        RecordingCallback<Product> cb = new RecordingCallback<>();

        api.getProductByBarcode("0000", cb);

        assertNull(cb.result);
        assertTrue(cb.error.isNotFound());
    }

    @Test
    public void createProductSendsNoId() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody(RICE));
        Product draft = new Product(0, "7896004000015", "Arroz 5kg", Category.KITCHEN, Unit.KG, 5, null);
        RecordingCallback<Product> cb = new RecordingCallback<>();

        api.createProduct(draft, cb);

        assertEquals(1, cb.result.id);
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/products", request.getPath());
        assertTrue(request.getHeader("Content-Type").startsWith("application/json"));
        JsonObject body = new JsonParser().parse(request.getBody().readUtf8()).getAsJsonObject();
        assertFalse(body.has("id"));
        assertEquals("kitchen", body.get("category").getAsString());
        assertEquals("kg", body.get("unit").getAsString());
    }

    @Test
    public void sendMovementsPostsTheBatchAndReturnsItsId() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"id\":42}"));
        MovementBatch batch = new MovementBatch(Movement.Type.IN, "2026-09-22T13:05:00Z",
                Collections.singletonList(new MovementBatch.Item(1, 2, 10)));
        RecordingCallback<Long> cb = new RecordingCallback<>();

        api.sendMovements(batch, cb);

        assertEquals(Long.valueOf(42), cb.result);
        RecordedRequest request = server.takeRequest();
        assertEquals("/api/movements", request.getPath());
        JsonObject body = new JsonParser().parse(request.getBody().readUtf8()).getAsJsonObject();
        assertEquals("in", body.get("type").getAsString());
        assertEquals("2026-09-22T13:05:00Z", body.get("at").getAsString());
        JsonObject item = body.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals(1, item.get("productId").getAsLong());
        assertEquals(2, item.get("packages").getAsInt());
        assertEquals(10.0, item.get("amount").getAsDouble(), 0.0);
    }

    @Test
    public void inventoryAndMovementsParseLists() throws Exception {
        server.enqueue(new MockResponse().setBody("[{\"product\":" + RICE
                + ",\"amount\":15,\"packages\":3,\"updatedAt\":\"2026-09-22T13:05:00Z\"}]"));
        server.enqueue(new MockResponse().setBody("[{\"id\":301,\"productId\":1,\"type\":\"out\","
                + "\"amount\":5,\"packages\":1,\"at\":\"2026-09-22T13:05:00Z\"}]"));
        RecordingCallback<List<InventoryItem>> inventory = new RecordingCallback<>();
        RecordingCallback<List<Movement>> movements = new RecordingCallback<>();

        api.getInventory(inventory);
        api.getMovements(1, movements);

        assertEquals(1, inventory.result.size());
        assertEquals(15.0, inventory.result.get(0).amount, 0.0);
        assertEquals("Arroz 5kg", inventory.result.get(0).product.name);
        assertEquals(Movement.Type.OUT, movements.result.get(0).type);
        assertEquals("/api/inventory", server.takeRequest().getPath());
        assertEquals("/api/products/1/movements", server.takeRequest().getPath());
    }

    @Test
    public void serverErrorAndBrokenJsonAreErrors() {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setBody("not json"));
        RecordingCallback<List<InventoryItem>> first = new RecordingCallback<>();
        RecordingCallback<List<InventoryItem>> second = new RecordingCallback<>();

        api.getInventory(first);
        api.getInventory(second);

        assertEquals(500, first.error.statusCode);
        assertNotNull(second.error);
        assertNull(second.result);
    }

    @Test
    public void unreachableServerIsStatusZero() throws Exception {
        String url = server.url("/").toString();
        server.shutdown();
        RecordingCallback<List<InventoryItem>> cb = new RecordingCallback<>();

        apiFor(url).getInventory(cb);

        assertEquals(0, cb.error.statusCode);
    }

    @Test
    public void baseUrlIsNormalized() {
        assertEquals("http://192.168.0.10:8080/", HttpInventoryApi.parseBaseUrl(" 192.168.0.10:8080 ").toString());
        assertEquals("http://host/api/", HttpInventoryApi.parseBaseUrl("http://host/api").toString());
        assertNull(HttpInventoryApi.parseBaseUrl(""));
        assertNull(HttpInventoryApi.parseBaseUrl(null));
        assertNull(HttpInventoryApi.parseBaseUrl("http://"));
    }
}
