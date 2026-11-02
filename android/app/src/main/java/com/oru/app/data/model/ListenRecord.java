package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ListenRecord {
    @SerializedName("id")
    public int id;

    @SerializedName("song_id")
    public int songId;

    @SerializedName("listened_ms")
    public int listenedMs;

    @SerializedName("created_at")
    public String createdAt;
}
