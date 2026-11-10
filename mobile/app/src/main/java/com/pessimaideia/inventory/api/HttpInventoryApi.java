package com.pessimaideia.inventory.api;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.pessimaideia.inventory.model.InventoryItem;
import com.pessimaideia.inventory.model.LoginResult;
import com.pessimaideia.inventory.model.Movement;
import com.pessimaideia.inventory.model.MovementBatch;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.model.RecipeResult;
import com.pessimaideia.inventory.model.Suggestion;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HttpInventoryApi implements InventoryApi {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Type INVENTORY_LIST = new TypeToken<List<InventoryItem>>() {}.getType();
    private static final Type MOVEMENT_LIST = new TypeToken<List<Movement>>() {}.getType();

    private static class CreatedId {
        long id;
    }

    private final HttpUrl baseUrl;
    private final OkHttpClient client;
    private final Executor background;
    private final MainThreadPoster mainThread;
    private final Gson gson = new Gson();
    @Nullable
    private final String token;
    @Nullable
    private final Runnable onUnauthorized;

    public HttpInventoryApi(HttpUrl baseUrl, OkHttpClient client, Executor background,
                            MainThreadPoster mainThread, @Nullable String token,
                            @Nullable Runnable onUnauthorized) {
        this.baseUrl = baseUrl;
        this.client = client;
        this.background = background;
        this.mainThread = mainThread;
        this.token = token;
        this.onUnauthorized = onUnauthorized;
    }

    public static HttpInventoryApi forAndroid(HttpUrl baseUrl, @Nullable String token,
                                              @Nullable Runnable onUnauthorized) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        return new HttpInventoryApi(baseUrl, client, Executors.newSingleThreadExecutor(),
                new AndroidMainThreadPoster(), token, onUnauthorized);
    }

    @Nullable
    public static HttpUrl parseBaseUrl(@Nullable String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return null;
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://" + trimmed;
        }
        if (!trimmed.endsWith("/")) trimmed = trimmed + "/";
        return HttpUrl.parse(trimmed);
    }

    public void login(String username, String password, ApiCallback<LoginResult> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        HttpUrl url = url("login").build();
        run(callback, () -> gson.fromJson(call(post(url, body.toString())), LoginResult.class));
    }

    public void logout(ApiCallback<Boolean> callback) {
        HttpUrl url = url("logout").build();
        run(callback, () -> {
            call(post(url, ""));
            return Boolean.TRUE;
        });
    }

    @Override
    public void getProductByBarcode(String barcode, ApiCallback<Product> callback) {
        HttpUrl url = url("products/by-barcode").addPathSegment(barcode).build();
        run(callback, () -> gson.fromJson(call(get(url)), Product.class));
    }

    @Override
    public void createProduct(Product product, ApiCallback<Product> callback) {
        JsonObject body = gson.toJsonTree(product).getAsJsonObject();
        body.remove("id");
        HttpUrl url = url("products").build();
        run(callback, () -> gson.fromJson(call(post(url, body.toString())), Product.class));
    }

    @Override
    public void sendMovements(MovementBatch batch, ApiCallback<Long> callback) {
        HttpUrl url = url("movements").build();
        run(callback, () -> gson.fromJson(call(post(url, gson.toJson(batch))), CreatedId.class).id);
    }

    @Override
    public void getInventory(ApiCallback<List<InventoryItem>> callback) {
        HttpUrl url = url("inventory").build();
        run(callback, () -> gson.<List<InventoryItem>>fromJson(call(get(url)), INVENTORY_LIST));
    }

    @Override
    public void getProduct(long id, ApiCallback<Product> callback) {
        HttpUrl url = url("products").addPathSegment(String.valueOf(id)).build();
        run(callback, () -> gson.fromJson(call(get(url)), Product.class));
    }

    @Override
    public void getMovements(long productId, ApiCallback<List<Movement>> callback) {
        HttpUrl url = url("products").addPathSegment(String.valueOf(productId))
                .addPathSegment("movements").build();
        run(callback, () -> gson.<List<Movement>>fromJson(call(get(url)), MOVEMENT_LIST));
    }

    @Override
    public void lookup(String barcode, ApiCallback<Suggestion> callback) {
        HttpUrl url = url("lookup").addPathSegment(barcode).build();
        run(callback, () -> gson.fromJson(call(get(url)), Suggestion.class));
    }

    @Override
    public void suggestRecipes(String hint, String lang, boolean refresh, ApiCallback<RecipeResult> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("hint", hint);
        body.addProperty("lang", lang);
        body.addProperty("refresh", refresh);
        HttpUrl url = url("recipes/suggest").build();
        OkHttpClient slow = client.newBuilder().readTimeout(110, TimeUnit.SECONDS).build();
        run(callback, () -> gson.fromJson(call(slow, post(url, body.toString())), RecipeResult.class));
    }

    private HttpUrl.Builder url(String path) {
        return baseUrl.newBuilder().addPathSegments(path);
    }

    private Request get(HttpUrl url) {
        return request(url).get().build();
    }

    private Request post(HttpUrl url, String json) {
        return request(url).post(RequestBody.create(JSON, json)).build();
    }

    private Request.Builder request(HttpUrl url) {
        Request.Builder builder = new Request.Builder().url(url);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return builder;
    }

    private String call(Request request) throws ApiException {
        return call(client, request);
    }

    private static String call(OkHttpClient client, Request request) throws ApiException {
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                throw new ApiException(response.code(), "HTTP " + response.code());
            }
            return text;
        } catch (IOException e) {
            throw new ApiException(0, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private <T> void run(ApiCallback<T> callback, Callable<T> work) {
        background.execute(() -> {
            try {
                T result = work.call();
                if (result == null) throw new ApiException(0, "Empty response");
                mainThread.post(() -> callback.onSuccess(result));
            } catch (ApiException e) {
                if (e.statusCode == 401 && token != null && onUnauthorized != null) {
                    mainThread.post(onUnauthorized);
                }
                mainThread.post(() -> callback.onError(e));
            } catch (JsonParseException e) {
                mainThread.post(() -> callback.onError(new ApiException(0, "Invalid response")));
            } catch (Exception e) {
                mainThread.post(() -> callback.onError(new ApiException(0, e.toString())));
            }
        });
    }
}
