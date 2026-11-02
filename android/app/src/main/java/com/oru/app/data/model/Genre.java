package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class Genre {
    @SerializedName("id")
    public int id;

    @SerializedName("name")
    public String name;

    @SerializedName("slug")
    public String slug;

    @SerializedName("song_count")
    public int songCount;
}
