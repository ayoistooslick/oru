package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** A playlist: the songs it holds are only listed when it is read on its own. */
public class Playlist {

    @SerializedName("id")
    public int id;

    @SerializedName("owner_id")
    public int ownerId;

    @SerializedName("title")
    public String title;

    @SerializedName("is_public")
    public boolean isPublic;

    @SerializedName("song_ids")
    public List<Integer> songIds;

    @SerializedName("songs")
    public List<Song> songs;

    @SerializedName("song_count")
    public int totalSongs;

    /**
     * The playlist's own square artwork, or the first track's when it has none.
     * {@link #hasCover} says which of the two this is.
     */
    @SerializedName("cover_url")
    public String coverUrl;

    /** The wide picture behind the playlist page, when one was uploaded. */
    @SerializedName("banner_url")
    public String bannerUrl;

    @SerializedName("has_cover")
    public boolean hasCover;

    /** Bumped by the API whenever either picture is replaced. */
    @SerializedName("image_version")
    public int imageVersion;

    @SerializedName("owner")
    public Owner owner;

    /** A stable, cache-busting address for the cover or banner artwork. */
    public String coverUrlVersioned() {
        return com.oru.app.util.MediaUrl.versioned(coverUrl, imageVersion);
    }

    public String bannerUrlVersioned() {
        return com.oru.app.util.MediaUrl.versioned(bannerUrl, imageVersion);
    }

    public int songCount() {
        if (songs != null) {
            return songs.size();
        }
        if (songIds != null) {
            return songIds.size();
        }
        return totalSongs;
    }

    public static class Owner {
        @SerializedName("handle")
        public String handle;

        @SerializedName("display_name")
        public String displayName;
    }
}
