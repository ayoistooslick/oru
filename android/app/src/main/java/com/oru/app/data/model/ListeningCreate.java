package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ListeningCreate {
    @SerializedName("listened_ms")
    public int listenedMs;

    public ListeningCreate(int listenedMs) {
        this.listenedMs = listenedMs;
    }
}
