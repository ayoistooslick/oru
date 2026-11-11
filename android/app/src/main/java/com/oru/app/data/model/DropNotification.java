package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * One "an artist you follow just dropped something" row.
 *
 * The API sends everything a row needs to draw itself — who, what, and the
 * letterbox art — so the list is a single request. A drop that has since come
 * down keeps its row but loses its href, the same as on the web.
 */
public class DropNotification {

    @SerializedName("id")
    public int id;

    /** "new_song" or "new_release". */
    @SerializedName("kind")
    public String kind;

    @SerializedName("created_at")
    public String createdAt;

    /** Null until opened. */
    @SerializedName("read_at")
    public String readAt;

    @SerializedName("artist")
    public ArtistRef artist;

    @SerializedName("song")
    public SongRef song;

    @SerializedName("release")
    public ReleaseRef release;

    /** The web path this row opens, or null when there is nothing to open. */
    @SerializedName("href")
    public String href;

    public boolean isUnread() {
        return readAt == null || readAt.isEmpty();
    }

    /** "New song" or "New album", the way the row labels it. */
    public String kindLabel() {
        if ("new_release".equals(kind)) {
            String collection = release != null ? release.kind : null;
            if ("album".equals(collection)) return "New album";
            if ("ep".equals(collection)) return "New EP";
            return "New release";
        }
        return "New song";
    }

    public String title() {
        if (song != null && song.title != null && !song.title.isEmpty()) {
            return song.title;
        }
        if (release != null && release.title != null && !release.title.isEmpty()) {
            return release.title;
        }
        return kindLabel();
    }

    public String artistName() {
        return artist != null && artist.name != null ? artist.name : "An artist you follow";
    }

    public String coverUrl() {
        if (song != null && song.coverUrl != null) return song.coverUrl;
        if (release != null) return release.coverUrl;
        return null;
    }

    public static class ArtistRef {
        @SerializedName("id")
        public int id;
        @SerializedName("name")
        public String name;
        @SerializedName("handle")
        public String handle;
        @SerializedName("avatar_url")
        public String avatarUrl;
    }

    public static class SongRef {
        @SerializedName("id")
        public int id;
        @SerializedName("title")
        public String title;
        @SerializedName("cover_url")
        public String coverUrl;
        @SerializedName("is_available")
        public boolean available = true;
    }

    public static class ReleaseRef {
        @SerializedName("id")
        public int id;
        @SerializedName("title")
        public String title;
        @SerializedName("kind")
        public String kind;
        @SerializedName("cover_url")
        public String coverUrl;
        @SerializedName("is_available")
        public boolean available = true;
    }
}
