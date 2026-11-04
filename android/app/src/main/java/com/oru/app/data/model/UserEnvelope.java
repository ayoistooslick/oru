package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

/** The API returns {"user": {...}} from the current-user endpoint. */
public class UserEnvelope {

    @SerializedName("user")
    public User user;
}
