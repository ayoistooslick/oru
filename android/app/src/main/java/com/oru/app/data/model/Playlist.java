package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** A playlist: the songs it holds are only listed when it is read on its own. */
public class Playlist {

    @SerializedName("id")
    public int id;

    @SerializedName("owner_id")
    public int ownerId;

    @SerializedName("title")
    public String title;

    @SerializedName("is_public")
    public boolean isPublic;

    @SerializedName("song_ids")
    public List<Integer> songIds;

    @SerializedName("songs")
    public List<Song> songs;

    @SerializedName("song_count")
    public int totalSongs;

    /** The first track's artwork, since a playlist has none of its own. */
    @SerializedName("cover_url")
    public String coverUrl;

    @SerializedName("owner")
    public Owner owner;

    public int songCount() {
        if (songs != null) {
            return songs.size();
        }
        if (songIds != null) {
            return songIds.size();
        }
        return totalSongs;
    }

    public static class Owner {
        @SerializedName("handle")
        public String handle;

        @SerializedName("display_name")
        public String displayName;
    }
}
