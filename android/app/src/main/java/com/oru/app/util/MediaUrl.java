package com.oru.app.util;

/**
 * The API describes media with a path it serves itself
 * ("/api/v1/songs/12/cover"), but the Android player and image loader need a
 * full address. This joins the path onto the API's origin.
 */
public final class MediaUrl {

    private MediaUrl() {
    }

    public static String of(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String origin = com.oru.app.data.api.ApiClient.origin();
        return path.startsWith("/") ? origin + path : origin + "/" + path;
    }
}
