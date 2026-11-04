package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

/** A song someone shared from outside ORU, credited to its artist. */
public class ExternalShare {

    @SerializedName("id")
    public int id;

    @SerializedName("title")
    public String title;

    @SerializedName("artist_name")
    public String artistName;

    @SerializedName("url")
    public String url;

    @SerializedName("note")
    public String note;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("sharer")
    public Sharer sharer;

    public static class Sharer {
        @SerializedName("handle")
        public String handle;

        @SerializedName("display_name")
        public String displayName;
    }
}
