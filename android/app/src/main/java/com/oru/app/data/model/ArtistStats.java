package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ArtistStats {
    @SerializedName("total_listens")
    public int totalListens;

    @SerializedName("total_plays")
    public int totalPlays;

    @SerializedName("followers_count")
    public int followersCount;

    @SerializedName("likes_count")
    public int likesCount;

    @SerializedName("song_count")
    public int songCount;

    @SerializedName("release_count")
    public int releaseCount;

    @SerializedName("weekly_listens")
    public int weeklyListens;

    @SerializedName("weekly_growth")
    public int weeklyGrowth;
}
