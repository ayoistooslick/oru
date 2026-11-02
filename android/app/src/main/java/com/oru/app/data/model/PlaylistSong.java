package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class PlaylistSong {
    @SerializedName("id")
    public int id;

    @SerializedName("playlist_id")
    public int playlistId;

    @SerializedName("song_id")
    public int songId;

    @SerializedName("added_at")
    public String addedAt;

    @SerializedName("song")
    public Song song;
}
