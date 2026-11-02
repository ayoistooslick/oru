package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class PlaylistUpdate {
    @SerializedName("title")
    public String title;

    @SerializedName("is_public")
    public Boolean isPublic;

    public PlaylistUpdate(String title, Boolean isPublic) {
        this.title = title;
        this.isPublic = isPublic;
    }
}
