package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

/** The single-use code the browser brought back from the Google sign-in page. */
public class HandoffRequest {
    @SerializedName("code")
    public String code;

    public HandoffRequest(String code) {
        this.code = code;
    }
}
