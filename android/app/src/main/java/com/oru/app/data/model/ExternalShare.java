package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ExternalShare {
    @SerializedName("id")
    public int id;

    @SerializedName("song_id")
    public int songId;

    @SerializedName("platform")
    public String platform;

    @SerializedName("external_url")
    public String externalUrl;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("song")
    public Song song;
}
