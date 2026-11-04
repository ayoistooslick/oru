package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

/** One place in the weekly chart: the song and how often it was played. */
public class RankingEntry {

    @SerializedName("rank")
    public int rank;

    @SerializedName("plays")
    public int plays;

    @SerializedName("song")
    public Song song;
}
