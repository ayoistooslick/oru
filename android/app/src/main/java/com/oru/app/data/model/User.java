package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Listener / user object (from app/models.py User).
 */
public class User {

    @SerializedName("id")
    public int id;

    @SerializedName("email")
    public String email;

    @SerializedName("handle")
    public String handle;

    @SerializedName("display_name")
    public String displayName;

    @SerializedName("bio")
    public String bio;

    @SerializedName("role")
    public String role; // "listener" | "artist" | "admin"

    @SerializedName("avatar_url")
    public String avatarUrl;

    @SerializedName("banner_url")
    public String bannerUrl;

    @SerializedName("is_active")
    public boolean isActive;

    @SerializedName("created_at")
    public String createdAt;

    public boolean isArtist() {
        return "artist".equals(role) && artistProfile != null;
    }

    @SerializedName("artist_profile")
    public ArtistProfile artistProfile;
}

class ArtistProfile {
    @SerializedName("id")
    public int id;

    @SerializedName("artist_id")
    public int artistId;

    @SerializedName("stage_name")
    public String stageName;

    @SerializedName("bio")
    public String bio;

    @SerializedName("banner_url")
    public String bannerUrl;
}
