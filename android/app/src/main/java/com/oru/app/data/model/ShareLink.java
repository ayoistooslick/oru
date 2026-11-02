package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ShareLink {
    @SerializedName("id")
    public int id;

    @SerializedName("song_id")
    public int songId;

    @SerializedName("url")
    public String url;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("song")
    public Song song;
}
