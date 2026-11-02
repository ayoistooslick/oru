package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ArtistPost {
    @SerializedName("id")
    public int id;

    @SerializedName("artist_id")
    public int artistId;

    @SerializedName("body")
    public String body;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("artist")
    public Artist artist;
}
