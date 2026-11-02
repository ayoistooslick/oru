package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class AuthConfig {
    @SerializedName("google_client_id")
    public String googleClientId;

    @SerializedName("min_password_length")
    public int minPasswordLength;

    @SerializedName("require_display_name")
    public boolean requireDisplayName;
}
