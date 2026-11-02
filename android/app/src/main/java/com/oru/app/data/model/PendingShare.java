package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class PendingShare {
    @SerializedName("id")
    public int id;

    @SerializedName("song_id")
    public int songId;

    @SerializedName("share_status")
    public String shareStatus;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("song")
    public Song song;
}
