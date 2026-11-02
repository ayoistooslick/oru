package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ArtistPostCreate {
    @SerializedName("body")
    public String body;

    public ArtistPostCreate(String body) {
        this.body = body;
    }
}
