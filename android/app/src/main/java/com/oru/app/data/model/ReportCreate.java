package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ReportCreate {
    @SerializedName("target_type")
    public String targetType;

    @SerializedName("target_id")
    public int targetId;

    @SerializedName("reason")
    public String reason;

    @SerializedName("details")
    public String details;

    public ReportCreate(String targetType, int targetId, String reason,
            String details) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.details = details;
    }
}
