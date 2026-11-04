package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

/** The API answers a follow with the artist and whether you now follow them. */
public class FollowResult {

    @SerializedName("artist_id")
    public int artistId;

    @SerializedName("following")
    public boolean following;
}
