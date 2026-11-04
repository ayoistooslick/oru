package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

/** A short post from an artist's profile. */
public class ArtistPost {

    @SerializedName("id")
    public int id;

    @SerializedName("artist_id")
    public int artistId;

    @SerializedName("body")
    public String body;

    @SerializedName("is_hidden")
    public boolean isHidden;

    @SerializedName("created_at")
    public String createdAt;
}
