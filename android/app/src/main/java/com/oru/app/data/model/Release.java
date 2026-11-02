package com.oru.app.data.model;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class Release {
    @SerializedName("id")
    public int id;

    @SerializedName("artist_id")
    public int artistId;

    @SerializedName("title")
    public String title;

    @SerializedName("kind")
    public String kind; // "single" | "ep" | "album"

    @SerializedName("cover_url")
    public String coverUrl;

    @SerializedName("is_published")
    public boolean isPublished;

    @SerializedName("song_ids")
    public List<Integer> songIds;

    @SerializedName("songs")
    public List<Song> songs;

    @SerializedName("play_count")
    public int playCount;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("artist")
    public Artist artist;
}
