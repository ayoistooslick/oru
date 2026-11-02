package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Playlist {
    @SerializedName("id")
    public int id;

    @SerializedName("owner_id")
    public int ownerId;

    @SerializedName("title")
    public String title;

    @SerializedName("description")
    public String description;

    @SerializedName("cover_url")
    public String coverUrl;

    @SerializedName("is_public")
    public boolean isPublic;

    @SerializedName("song_count")
    public int songCount;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("owner")
    public User owner;

    @SerializedName("songs")
    public List<Song> songs;
}
