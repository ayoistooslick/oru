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

        /** Validation failures name the field that needs fixing. */
        @SerializedName("details")
        public java.util.List<ApiErrorDetail> details;
    }

    public static class ApiErrorDetail {
        @SerializedName("field")
        public String field;

        @SerializedName("message")
        public String message;
    }

    public static class ApiException extends Exception {
        public final String code;
        public final int statusCode;

        public ApiException(String code, String message) {
            this(code, message, 0);
        }

        public ApiException(String code, String message, int statusCode) {
            super(message);
            this.code = code;
            this.statusCode = statusCode;
        }
    }
}
