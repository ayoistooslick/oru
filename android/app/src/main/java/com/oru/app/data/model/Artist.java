package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

/** An artist profile, as the API's artist_data() describes it. */
public class Artist {

    @SerializedName("user_id")
    public int userId;

    /** Also used as the id when following this artist. */
    @SerializedName("id")
    public int id;

    @SerializedName("handle")
    public String handle;

    @SerializedName("stage_name")
    public String stageName;

    @SerializedName("bio")
    public String bio;

    @SerializedName("avatar_url")
    public String avatarUrl;

    @SerializedName("banner_url")
    public String bannerUrl;

    /** Only on the artist's own page. */
    @SerializedName("published_song_count")
    public int publishedSongCount;

    /** Only on the artist's own page. */
    @SerializedName("follower_count")
    public int followerCount;

    /** The artist id the API uses for follows and lookups. */
    public int artistId() {
        return userId != 0 ? userId : id;
    }
}
