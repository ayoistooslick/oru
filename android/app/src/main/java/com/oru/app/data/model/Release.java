package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** A single, EP or album: the songs it groups and whether it is out yet. */
public class Release {

    @SerializedName("id")
    public int id;

    @SerializedName("artist_id")
    public int artistId;

    @SerializedName("title")
    public String title;

    /** "single", "ep" or "album". */
    @SerializedName("kind")
    public String kind;

    /** "draft", "scheduled" or "published". */
    @SerializedName("status")
    public String status;

    @SerializedName("published_at")
    public String publishedAt;

    @SerializedName("song_ids")
    public List<Integer> songIds;

    /** Filled in when the release is read on its own. */
    @SerializedName("songs")
    public List<Song> songs;

    @SerializedName("song_count")
    public int songCount;

    /** The first track's artwork, since a release has none of its own. */
    @SerializedName("cover_url")
    public String coverUrl;

    /** A release is out once it has a publication date; list items carry only
     * that, not the status. */
    public boolean isPublished() {
        return publishedAt != null || "published".equals(status);
    }
}
