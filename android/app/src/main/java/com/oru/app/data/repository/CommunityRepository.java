package com.oru.app.data.repository;

import com.google.gson.Gson;
import com.oru.app.data.api.ApiCall;
import com.oru.app.data.api.ApiService;
import com.oru.app.data.model.*;

import java.util.List;

/**
 * Social operations: follows, likes, playlists and the listener's library.
 */
public class CommunityRepository {

    private final ApiService api;
    private final Gson gson;

    public CommunityRepository(ApiService api, Gson gson) {
        this.api = api;
        this.gson = gson;
    }

    public void followArtist(int artistId, Callback<FollowResult> cb) {
        ApiCall.enqueue(api.followArtist(artistId), gson,
                "Couldn't follow this artist", cb);
    }

    public void unfollowArtist(int artistId, Callback<FollowResult> cb) {
        ApiCall.enqueue(api.unfollowArtist(artistId), gson,
                "Couldn't unfollow this artist", cb);
    }

    public void likeSong(int songId, Callback<LikeResult> cb) {
        ApiCall.enqueue(api.likeSong(songId), gson, "Couldn't like this song",
                cb);
    }

    public void unlikeSong(int songId, Callback<LikeResult> cb) {
        ApiCall.enqueue(api.unlikeSong(songId), gson,
                "Couldn't remove the like", cb);
    }

    public void getMyLikes(Callback<List<Song>> cb) {
        ApiCall.enqueue(api.getMyLikes(), gson, "Couldn't load your likes", cb);
    }

    public void getFollowing(Callback<List<Artist>> cb) {
        ApiCall.enqueue(api.getFollowing(), gson,
                "Couldn't load who you follow", cb);
    }

    public void createPlaylist(String title, boolean isPublic,
            Callback<Playlist> cb) {
        ApiCall.enqueue(api.createPlaylist(new PlaylistCreate(title, isPublic)),
                gson, "Couldn't create the playlist", cb);
    }

    public void listPlaylists(Callback<List<Playlist>> cb) {
        ApiCall.enqueue(api.listPlaylists(), gson,
                "Couldn't load your playlists", cb);
    }

    public void getPlaylist(int playlistId, Callback<Playlist> cb) {
        ApiCall.enqueue(api.getPlaylist(playlistId), gson,
                "Couldn't load this playlist", cb);
    }

    public void updatePlaylist(int playlistId, String title, Boolean isPublic,
            Callback<Playlist> cb) {
        ApiCall.enqueue(
                api.updatePlaylist(playlistId, new PlaylistUpdate(title, isPublic)),
                gson, "Couldn't update the playlist", cb);
    }

    public void deletePlaylist(int playlistId, Callback<Void> cb) {
        ApiCall.enqueue(api.deletePlaylist(playlistId), gson,
                "Couldn't delete that playlist", cb);
    }

    public void addSongToPlaylist(int playlistId, int songId,
            Callback<PlaylistSong> cb) {
        ApiCall.enqueue(api.addSongToPlaylist(playlistId, songId), gson,
                "Couldn't add that song", cb);
    }

    public void removeSongFromPlaylist(int playlistId, int songId,
            Callback<Void> cb) {
        ApiCall.enqueue(api.removeSongFromPlaylist(playlistId, songId), gson,
                "Couldn't remove that song", cb);
    }
}
