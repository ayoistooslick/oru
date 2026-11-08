package com.oru.app.util;

import android.net.Uri;

/**
 * The API describes media with a path it serves itself
 * ("/api/v1/songs/12/cover"), but the Android player and image loader need a
 * full address. Production publishes one function route, so the path has to
 * travel in its __path parameter exactly like every other API call; asking for
 * the bare path returns the web page instead of the file.
 */
public final class MediaUrl {

    private MediaUrl() {
    }

    /**
     * The same address with a version tag appended.
     *
     * Replaceable pictures (covers, banners) keep their path, so an image loader
     * would happily show the old one; the tag makes a replaced picture a new
     * address. The API ignores the parameter, so a stale tag is harmless.
     */
    public static String versioned(String path, int version) {
        String url = of(path);
        if (url == null || version <= 0) {
            return url;
        }
        return url + "&v=" + version;
    }

    public static String of(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            String origin = com.oru.app.data.api.ApiClient.origin();
            if (!path.startsWith(origin)) {
                return path;
            }
            path = path.substring(origin.length());
        }
        String fullPath = path.startsWith("/") ? path : "/" + path;
        return com.oru.app.data.api.ApiClient.origin() + "/api?__path="
                + Uri.encode(fullPath);
    }
}
