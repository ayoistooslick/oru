package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class Report {
    @SerializedName("id")
    public int id;

    @SerializedName("target_type")
    public String targetType;

    @SerializedName("target_id")
    public int targetId;

    @SerializedName("reason")
    public String reason;

    @SerializedName("details")
    public String details;

    @SerializedName("status")
    public String status;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("decided_at")
    public String decidedAt;

    @SerializedName("action")
    public String action;
}
