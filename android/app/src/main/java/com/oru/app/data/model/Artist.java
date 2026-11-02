package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class Artist {
    @SerializedName("id")
    public int id;

    @SerializedName("user_id")
    public int userId;

    @SerializedName("stage_name")
    public String stageName;

    @SerializedName("bio")
    public String bio;

    @SerializedName("banner_url")
    public String bannerUrl;

    @SerializedName("avatar_url")
    public String avatarUrl;

    @SerializedName("is_verified")
    public boolean isVerified;

    @SerializedName("followers_count")
    public int followersCount;

    @SerializedName("likes_count")
    public int likesCount;

    @SerializedName("listens_count")
    public int listensCount;

    @SerializedName("song_count")
    public int songCount;

    @SerializedName("release_count")
    public int releaseCount;

    @SerializedName("is_followed")
    public boolean isFollowed;

    @SerializedName("created_at")
    public String createdAt;
}
