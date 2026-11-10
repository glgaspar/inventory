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
                Runnable::run, MainThreadPoster.DIRECT, null, null);
    }

    private HttpInventoryApi withToken(String token, Runnable onUnauthorized) {
        return new HttpInventoryApi(HttpInventoryApi.parseBaseUrl(server.url("/api/").toString()),
                new OkHttpClient(), Runnable::run, MainThreadPoster.DIRECT, token, onUnauthorized);
    }

    @Test
    public void lookup() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"barcode\":\"789\",\"name\":\"Lava louças Ypê\",\"brand\":\"Ypê\","
                + "\"category\":\"cleaning\",\"unit\":\"mL\",\"packageSize\":500,\"imageUrl\":\"http://h/images/x\","
                + "\"source\":\"openproductsfacts\"}"));
        RecordingCallback<com.pessimaideia.inventory.model.Suggestion> cb = new RecordingCallback<>();

        api.lookup("789", cb);

        assertNull(cb.error);
        assertEquals("Lava louças Ypê", cb.result.name);
        assertEquals(Category.CLEANING, cb.result.category);
        assertEquals(Unit.ML, cb.result.unit);
        assertEquals(500.0, cb.result.packageSize, 0.0);
        assertEquals("http://h/images/x", cb.result.imageUrl);
        assertEquals("/api/lookup/789", server.takeRequest().getPath());
    }

    @Test
    public void lookupNotFound() {
        server.enqueue(new MockResponse().setResponseCode(404));
        RecordingCallback<com.pessimaideia.inventory.model.Suggestion> cb = new RecordingCallback<>();
        api.lookup("000", cb);
        assertTrue(cb.error.isNotFound());
    }

    @Test
    public void recipes() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"recipes\":[{\"title\":\"Arroz\",\"summary\":\"\",\"minutes\":30,"
                + "\"servings\":4,\"ingredients\":[{\"name\":\"arroz\",\"amount\":\"1 xícara\",\"fromStock\":true}],"
                + "\"missing\":[\"alho\"],\"steps\":[\"Cozinhe\"]}],\"model\":\"m\",\"generatedAt\":\"x\",\"cached\":false}"));
        RecordingCallback<com.pessimaideia.inventory.model.RecipeResult> cb = new RecordingCallback<>();

        api.suggestRecipes("jantar", "pt", true, cb);

        assertNull(cb.error);
        assertEquals("Arroz", cb.result.recipes.get(0).title);
        assertTrue(cb.result.recipes.get(0).ingredients.get(0).fromStock);
        assertEquals("alho", cb.result.recipes.get(0).missing.get(0));
        RecordedRequest request = server.takeRequest();
        assertEquals("/api/recipes/suggest", request.getPath());
        JsonObject body = new JsonParser().parse(request.getBody().readUtf8()).getAsJsonObject();
        assertEquals("jantar", body.get("hint").getAsString());
        assertEquals("pt", body.get("lang").getAsString());
        assertTrue(body.get("refresh").getAsBoolean());
    }

    @Test
    public void movementHasWhoSentIt() {
        server.enqueue(new MockResponse().setBody("[{\"id\":1,\"batchId\":4,\"productId\":1,\"type\":\"out\","
                + "\"amount\":2,\"packages\":0,\"at\":\"2026-09-22T13:05:00Z\",\"userId\":3,\"username\":\"maria\"}]"));
        RecordingCallback<List<Movement>> cb = new RecordingCallback<>();
        api.getMovements(1, cb);
        assertEquals("maria", cb.result.get(0).username);
        assertEquals(Movement.Type.OUT, cb.result.get(0).type);
    }

    @Test
    public void sendsTokenOnEveryRequest() throws Exception {
        server.enqueue(new MockResponse().setBody("[]"));
        server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"id\":7}"));
        HttpInventoryApi authed = withToken("abc123", null);

        authed.getInventory(new RecordingCallback<>());
        authed.sendMovements(new MovementBatch(Movement.Type.IN, "2026-09-22T13:05:00Z",
                Collections.<MovementBatch.Item>emptyList()), new RecordingCallback<>());

        assertEquals("Bearer abc123", server.takeRequest().getHeader("Authorization"));
        assertEquals("Bearer abc123", server.takeRequest().getHeader("Authorization"));
    }

    @Test
    public void noTokenNoHeader() throws Exception {
        server.enqueue(new MockResponse().setBody("[]"));
        api.getInventory(new RecordingCallback<>());
        assertNull(server.takeRequest().getHeader("Authorization"));
    }

    @Test
    public void unauthorizedCallsBack() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"message\":\"login required\"}"));
        int[] calls = {0};
        RecordingCallback<List<InventoryItem>> cb = new RecordingCallback<>();

        withToken("expired", () -> calls[0]++).getInventory(cb);

        assertEquals(1, calls[0]);
        assertNotNull(cb.error);
        assertEquals(401, cb.error.statusCode);
    }

    @Test
    public void otherErrorsDoNotLogOut() {
        server.enqueue(new MockResponse().setResponseCode(404));
        server.enqueue(new MockResponse().setResponseCode(403));
        int[] calls = {0};
        HttpInventoryApi authed = withToken("t", () -> calls[0]++);

        authed.getProduct(9, new RecordingCallback<>());
        authed.getInventory(new RecordingCallback<>());

        assertEquals(0, calls[0]);
    }

    @Test
    public void login() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"token\":\"tok\",\"user\":{\"id\":3,\"username\":\"maria\",\"admin\":false}}"));
        RecordingCallback<com.pessimaideia.inventory.model.LoginResult> cb = new RecordingCallback<>();

        api.login("maria", "secret pass", cb);

        assertNull(cb.error);
        assertEquals("tok", cb.result.token);
        assertEquals("maria", cb.result.user.username);
        assertFalse(cb.result.user.admin);
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/login", request.getPath());
        JsonObject body = new JsonParser().parse(request.getBody().readUtf8()).getAsJsonObject();
        assertEquals("maria", body.get("username").getAsString());
        assertEquals("secret pass", body.get("password").getAsString());
    }

    @Test
    public void wrongPasswordIs401WithoutLogoutCallback() {
        server.enqueue(new MockResponse().setResponseCode(401));
        RecordingCallback<com.pessimaideia.inventory.model.LoginResult> cb = new RecordingCallback<>();

        api.login("maria", "wrong", cb);

        assertEquals(401, cb.error.statusCode);
    }

    @Test
    public void logout() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));
        RecordingCallback<Boolean> cb = new RecordingCallback<>();

        withToken("tok", null).logout(cb);

        assertNull(cb.error);
        assertTrue(cb.result);
        RecordedRequest request = server.takeRequest();
        assertEquals("/api/logout", request.getPath());
        assertEquals("Bearer tok", request.getHeader("Authorization"));
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
