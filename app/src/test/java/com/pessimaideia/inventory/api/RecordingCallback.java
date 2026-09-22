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