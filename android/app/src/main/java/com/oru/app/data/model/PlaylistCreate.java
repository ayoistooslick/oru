package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class PlaylistCreate {
    @SerializedName("title")
    public String title;

    @SerializedName("is_public")
    public boolean isPublic;

    public PlaylistCreate(String title, boolean isPublic) {
        this.title = title;
        this.isPublic = isPublic;
    }
}
