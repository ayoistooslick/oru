package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class LikeResult {
    @SerializedName("liked")
    public boolean liked;

    @SerializedName("likes_count")
    public int likesCount;
}
