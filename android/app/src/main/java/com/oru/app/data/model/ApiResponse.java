package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Wraps backend JSON responses. The ORU API returns {"data": ..., "error": ...}
 * or plain objects. We keep a generic envelope.
 */
public class ApiResponse<T> {

    @SerializedName("data")
    public T data;

    @SerializedName("error")
    public ApiError error;

    @SerializedName("message")
    public String message;

    public boolean isSuccess() {
        return error == null;
    }

    public void throwIfError() throws ApiException {
        if (error != null) {
            throw new ApiException(error.code, error.message);
        }
    }

    public static class ApiError {
        @SerializedName("code")
        public String code;

        @SerializedName("message")
        public String message;
    }

    public static class ApiException extends Exception {
        public final String code;

        public ApiException(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
