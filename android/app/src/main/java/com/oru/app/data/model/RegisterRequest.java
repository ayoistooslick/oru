package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class RegisterRequest {
    @SerializedName("email")
    public String email;

    @SerializedName("password")
    public String password;

    @SerializedName("handle")
    public String handle;

    @SerializedName("display_name")
    public String displayName;

    public RegisterRequest(String email, String password, String handle,
            String displayName) {
        this.email = email;
        this.password = password;
        this.handle = handle;
        this.displayName = displayName;
    }
}
