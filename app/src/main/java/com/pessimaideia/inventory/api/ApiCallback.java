package com.pessimaideia.inventory.api;

public interface ApiCallback<T> {
    void onSuccess(T result);
    void onError(ApiException error);
}