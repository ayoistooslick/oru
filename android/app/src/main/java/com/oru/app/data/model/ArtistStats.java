package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

/** An artist's own numbers, over all time and over the last seven days. */
public class ArtistStats {

    @SerializedName("artist_id")
    public int artistId;

    @SerializedName("published_songs")
    public int publishedSongs;

    @SerializedName("likes")
    public int likes;

    /** Plays long enough to count toward discovery. */
    @SerializedName("qualified_listens")
    public int qualifiedListens;

    @SerializedName("weekly_plays")
    public int weeklyPlays;

    @SerializedName("weekly_likes")
    public int weeklyLikes;

    @SerializedName("weekly_top_song")
    public TopSong weeklyTopSong;

    @SerializedName("window_days")
    public int windowDays;

    public static class TopSong {
        @SerializedName("id")
        public int id;

        @SerializedName("title")
        public String title;

        @SerializedName("plays")
        public int plays;
    }
}
