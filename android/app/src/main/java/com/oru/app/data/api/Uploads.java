package com.oru.app.data.api;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/** Builds the parts for the API's multipart upload forms. */
public final class Uploads {

    private Uploads() {
    }

    public static RequestBody text(String value) {
        return RequestBody.create(value == null ? "" : value,
                MediaType.parse("text/plain"));
    }

    public static RequestBody number(long value) {
        return text(Long.toString(value));
    }

    public static MultipartBody.Part file(String field, String name, String mime,
            File file) {
        MediaType type = MediaType.parse(
                mime != null ? mime : "application/octet-stream");
        return MultipartBody.Part.createFormData(field,
                name != null ? name : file.getName(),
                RequestBody.create(file, type));
    }
}
