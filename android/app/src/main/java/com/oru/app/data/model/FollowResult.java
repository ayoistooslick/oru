package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class FollowResult {
    @SerializedName("followed")
    public boolean followed;

    @SerializedName("followers_count")
    public int followersCount;
}
