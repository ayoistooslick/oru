package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class SongCreate {
    @SerializedName("title")
    public String title;

    @SerializedName("description")
    public String description;

    @SerializedName("cover_url")
    public String coverUrl;

    @SerializedName("audio_url")
    public String audioUrl;

    public SongCreate(String title, String description, String coverUrl,
            String audioUrl) {
        this.title = title;
        this.description = description;
        this.coverUrl = coverUrl;
        this.audioUrl = audioUrl;
    }
}
