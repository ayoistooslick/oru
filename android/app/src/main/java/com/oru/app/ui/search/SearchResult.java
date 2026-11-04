package com.oru.app.ui.search;

import com.oru.app.data.model.Artist;
import com.oru.app.data.model.Playlist;
import com.oru.app.data.model.Song;

public class SearchResult {
    public enum Type { SONG, ARTIST, PLAYLIST }

    public final Type type;
    public final Song song;
    public final Artist artist;
    public final Playlist playlist;
    public final String title;
    public final String subtitle;
    public final String meta;

    public SearchResult(Song song, Type type) {
        this.type = type;
        this.song = song;
        this.artist = null;
        this.playlist = null;
        this.title = song.title;
        this.subtitle = song.artistLabel();
        String length = song.durationLabel();
        String genres = song.genreLabel();
        this.meta = length.isEmpty() ? genres
                : genres.isEmpty() ? length : length + " · " + genres;
    }

    public SearchResult(Artist artist, Type type) {
        this.type = type;
        this.song = null;
        this.artist = artist;
        this.playlist = null;
        this.title = artist.stageName;
        this.subtitle = artist.bio != null ? artist.bio : "";
        this.meta = artist.handle != null ? "@" + artist.handle : "";
    }

    public SearchResult(Playlist playlist, Type type) {
        this.type = type;
        this.song = null;
        this.artist = null;
        this.playlist = playlist;
        this.title = playlist.title;
        this.subtitle = playlist.isPublic ? "Public playlist" : "Private playlist";
        this.meta = playlist.songCount() + " songs";
    }
}
