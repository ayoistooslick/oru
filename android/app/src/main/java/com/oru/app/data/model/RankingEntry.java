package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class RankingEntry {
    @SerializedName("rank")
    public int rank;

    @SerializedName("artist_id")
    public int artistId;

    @SerializedName("artist")
    public Artist artist;

    @SerializedName("weekly_listens")
    public int weeklyListens;

    @SerializedName("weekly_growth")
    public int weeklyGrowth;
}
