package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class Song {
    @SerializedName("id")
    public int id;

    @SerializedName("artist_id")
    public int artistId;

    @SerializedName("title")
    public String title;

    @SerializedName("description")
    public String description;

    @SerializedName("cover_url")
    public String coverUrl;

    @SerializedName("audio_url")
    public String audioUrl;

    @SerializedName("duration_ms")
    public int durationMs;

    @SerializedName("plays_count")
    public int playsCount;

    @SerializedName("likes_count")
    public int likesCount;

    @SerializedName("listens_count")
    public int listensCount;

    @SerializedName("is_published")
    public boolean isPublished;

    @SerializedName("is_liked")
    public boolean isLiked;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("artist")
    public Artist artist;

    @SerializedName("share_status")
    public String shareStatus;
}
