package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ArtistProfileCreate {
    @SerializedName("stage_name")
    public String stageName;

    public ArtistProfileCreate(String stageName) {
        this.stageName = stageName;
    }
}
