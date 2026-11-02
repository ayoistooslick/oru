package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ShareDecision {
    @SerializedName("action")
    public String action; // "approve" | "reject"

    public ShareDecision(String action) {
        this.action = action;
    }
}
