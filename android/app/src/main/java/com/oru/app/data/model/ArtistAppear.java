package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ArtistAppear {
    @SerializedName("id")
    public int id;

    @SerializedName("song_id")
    public int songId;

    @SerializedName("song")
    public Song song;

    @SerializedName("featuring_artist_id")
    public int featuringArtistId;

    @SerializedName("featuring_artist")
    public Artist featuringArtist;
}
