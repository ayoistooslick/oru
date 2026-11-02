package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class DiscoverResult {
    @SerializedName("songs")
    public List<Song> songs;

    @SerializedName("artists")
    public List<Artist> artists;

    @SerializedName("playlists")
    public List<Playlist> playlists;
}
