package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ShareCreate {
    @SerializedName("song_id")
    public int songId;

    public ShareCreate(int songId) {
        this.songId = songId;
    }
}
