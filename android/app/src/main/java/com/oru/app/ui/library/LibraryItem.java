package com.oru.app.ui.library;

import com.oru.app.data.model.Playlist;
import com.oru.app.data.model.Song;

import java.util.List;

public class LibraryItem {
    public enum Type { LIKES, PLAYLISTS, MY_SONGS, RELEASES }

    public final Type type;
    public final List<Song> songs;
    public final List<Playlist> playlists;
    public final String label;

    private LibraryItem(Type type, List<Song> songs, List<Playlist> playlists, String label) {
        this.type = type;
        this.songs = songs;
        this.playlists = playlists;
        this.label = label;
    }

    public static LibraryItem songs(List<Song> songs, Type type) {
        return new LibraryItem(type, songs, null, type.name().toLowerCase());
    }

    public static LibraryItem playlists(List<Playlist> playlists, Type type) {
        return new LibraryItem(type, null, playlists, type.name().toLowerCase());
    }
}
