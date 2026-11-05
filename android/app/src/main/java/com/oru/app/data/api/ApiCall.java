package com.oru.app.data.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oru.app.data.model.ApiResponse;
import com.oru.app.data.repository.Callback;

import java.io.IOException;

/**
 * One place that turns an API reply into a result or a failure.
 *
 * Every endpoint used to repeat the same block by hand, and every failure was
 * reported as a plain {@link IOException}, which the screens rendered as
 * "Connection error. Check your internet." even when the API had answered with
 * a perfectly good explanation. Failures now carry the API's own message.
 */
public final class ApiCall {

    private ApiCall() {
    }

    public static <T> void enqueue(retrofit2.Call<ApiResponse<T>> call, Gson gson,
            String fallback, Callback<T> cb) {
        call.enqueue(new retrofit2.Callback<ApiResponse<T>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<T>> ignored,
                    retrofit2.Response<ApiResponse<T>> response) {
                ApiResponse<T> body = response.body();
                if (!response.isSuccessful()) {
                    cb.onFailure(error(gson, response, fallback));
                    return;
                }
                // 204s and other empty replies still mean the call worked.
                if (body == null) {
                    cb.onSuccess(null);
                    return;
                }
                if (body.error != null) {
                    cb.onFailure(new ApiResponse.ApiException(body.error.code,
                            body.error.message));
                    return;
                }
                cb.onSuccess(body.data);
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<T>> ignored,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    /** Reads the API's error envelope out of an unsuccessful reply. */
    public static Throwable error(Gson gson, retrofit2.Response<?> response,
            String fallback) {
        if (response == null) {
            return new IOException(fallback);
        }
        String body = null;
        try {
            if (response.errorBody() != null) {
                body = response.errorBody().string();
            }
        } catch (IOException ignored) {
            // Fall back to the status code below.
        }
        if (body != null && !body.isEmpty()) {
            try {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                if (json.has("error") && json.get("error").isJsonObject()) {
                    ApiResponse.ApiError err = gson.fromJson(json.get("error"),
                            ApiResponse.ApiError.class);
                    if (err != null && err.message != null) {
                        String message = err.message;
                        if (err.details != null && !err.details.isEmpty()
                                && err.details.get(0).message != null) {
                            message = err.details.get(0).message;
                        }
                        return new ApiResponse.ApiException(err.code, message,
                                response.code());
                    }
                }
                if (json.has("detail") && json.get("detail").isJsonPrimitive()) {
                    return new ApiResponse.ApiException("http_" + response.code(),
                            json.get("detail").getAsString(), response.code());
                }
            } catch (RuntimeException ignored) {
                // Not the envelope we know; the status code is still useful.
            }
        }
        return new ApiResponse.ApiException("http_" + response.code(),
                "The server answered " + response.code() + ".", response.code());
    }
}
