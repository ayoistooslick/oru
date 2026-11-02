package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ExternalShareCreate {
    @SerializedName("song_id")
    public int songId;

    @SerializedName("platform")
    public String platform;

    public ExternalShareCreate(int songId, String platform) {
        this.songId = songId;
        this.platform = platform;
    }
}
