package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

/** A genre and how many published songs carry it. */
public class Genre {

    @SerializedName("name")
    public String name;

    @SerializedName("song_count")
    public int songCount;
}
