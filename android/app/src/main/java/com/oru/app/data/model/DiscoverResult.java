package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** /discover answers with the songs it has chosen, newest and best first. */
public class DiscoverResult {

    @SerializedName("items")
    public List<Song> songs;

    @SerializedName("ranking")
    public String ranking;
}
