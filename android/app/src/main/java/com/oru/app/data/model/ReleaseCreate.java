package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ReleaseCreate {
    @SerializedName("title")
    public String title;

    @SerializedName("kind")
    public String kind;

    @SerializedName("song_ids")
    public List<Integer> songIds;

    public ReleaseCreate(String title, String kind, List<Integer> songIds) {
        this.title = title;
        this.kind = kind;
        this.songIds = songIds;
    }
}
