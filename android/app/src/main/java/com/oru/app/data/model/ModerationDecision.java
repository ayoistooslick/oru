package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ModerationDecision {
    @SerializedName("action")
    public String action; // "hide" | "dismiss" | "restore"

    @SerializedName("reason")
    public String reason;

    public ModerationDecision(String action, String reason) {
        this.action = action;
        this.reason = reason;
    }
}
