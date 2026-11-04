package com.oru.app.data.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.oru.app.data.model.ApiResponse;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Reads the API's replies into {@link ApiResponse}.
 *
 * The API does not wrap everything in a "data" envelope. It answers:
 *   {"error": {"code": .., "message": ..}}        when something failed
 *   {"items": [..], "limit": ..}                  for collections
 *   {"song": {..}} / {"playlist": {..}} / ...     for a single record
 *   {"recorded": true, ..}                        for an action's own fields
 * so this adapter looks for the payload instead of assuming one shape. Without
 * it every reply deserialized to a null payload, which the repositories
 * reported as a failure and the screens showed as a connection problem.
 */
public final class ApiResponseAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!ApiResponse.class.isAssignableFrom(type.getRawType())) {
            return null;
        }
        Type payloadType = Object.class;
        if (type.getType() instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) type.getType()).getActualTypeArguments();
            if (args.length == 1) {
                payloadType = args[0];
            }
        }
        @SuppressWarnings({ "unchecked", "rawtypes" })
        TypeAdapter<T> adapter = (TypeAdapter) new Adapter(gson, payloadType).nullSafe();
        return adapter;
    }

    private static final class Adapter<T> extends TypeAdapter<T> {

        private final Gson gson;
        private final Type payloadType;
        private final boolean list;
        private final boolean empty;

        Adapter(Gson gson, Type payloadType) {
            this.gson = gson;
            this.payloadType = payloadType;
            this.list = payloadType instanceof ParameterizedType
                    && ((ParameterizedType) payloadType).getRawType() == List.class;
            this.empty = payloadType == Void.class || payloadType == void.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T read(JsonReader in) throws IOException {
            JsonElement root = gson.getAdapter(JsonElement.class).read(in);
            ApiResponse<Object> out = new ApiResponse<>();

            if (root == null || root.isJsonNull() || empty) {
                return (T) out;
            }
            if (root.isJsonArray()) {
                out.data = parse(root);
                return (T) out;
            }
            if (!root.isJsonObject()) {
                return (T) out;
            }

            JsonObject body = root.getAsJsonObject();
            JsonElement error = body.get("error");
            if (error != null && error.isJsonObject()) {
                out.error = gson.fromJson(error, ApiResponse.ApiError.class);
                return (T) out;
            }

            if (list) {
                JsonElement items = body.get("items");
                if (items != null && items.isJsonArray()) {
                    out.data = parse(items);
                    return (T) out;
                }
            }

            // A single record arrives named after itself ({"song": {..}}), while
            // an action's reply carries its own fields and is the payload.
            if (body.size() == 1) {
                Map.Entry<String, JsonElement> only =
                        body.entrySet().iterator().next();
                JsonElement value = only.getValue();
                if (value.isJsonObject() || (list && value.isJsonArray())) {
                    out.data = parse(value);
                    return (T) out;
                }
            }

            out.data = parse(body);
            return (T) out;
        }

        private Object parse(JsonElement element) {
            return gson.fromJson(element, payloadType);
        }

        @Override
        public void write(JsonWriter out, T value) throws IOException {
            gson.getAdapter(JsonElement.class).write(out, gson.toJsonTree(value));
        }
    }
}
