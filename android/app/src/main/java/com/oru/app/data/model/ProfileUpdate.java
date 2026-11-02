package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

public class ProfileUpdate {
    @SerializedName("display_name")
    public String displayName;

    @SerializedName("bio")
    public String bio;

    public ProfileUpdate(String displayName, String bio) {
        this.displayName = displayName;
        this.bio = bio;
    }
}
