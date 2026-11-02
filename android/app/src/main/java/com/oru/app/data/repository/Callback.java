package com.oru.app.data.repository;

/**
 * Simple success/failure callback used by repositories to avoid leaking
 * Retrofit types to the UI layer.
 */
public interface Callback<T> {
    void onSuccess(T result);
    void onFailure(Throwable error);
}
