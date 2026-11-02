package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class FeedItem {
    @SerializedName("type")
    public String type; // "song" | "release" | "post"

    @SerializedName("song")
    public Song song;

    @SerializedName("release")
    public Release release;

    @SerializedName("post")
    public ArtistPost post;

    @SerializedName("created_at")
    public String createdAt;
}
