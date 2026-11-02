package com.oru.app.data.repository;

import com.google.gson.Gson;
import com.oru.app.data.api.ApiService;
import com.oru.app.data.model.*;

import java.util.List;

import java.io.IOException;
import retrofit2.Response;

/**
 * Community operations: follows, likes, playlists, feed.
 */
public class CommunityRepository {

    private final ApiService api;
    private final Gson gson;

    public CommunityRepository(ApiService api, Gson gson) {
        this.api = api;
        this.gson = gson;
    }

    public void followArtist(int artistId, Callback<FollowResult> cb) {
        api.followArtist(artistId).enqueue(new retrofit2.Callback<ApiResponse<FollowResult>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<FollowResult>> call,
                    retrofit2.Response<ApiResponse<FollowResult>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("follow failed: "
                                + response.code()));
                        return;
                    }
                    ApiResponse<FollowResult> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "follow failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<FollowResult>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void unfollowArtist(int artistId, Callback<FollowResult> cb) {
        api.unfollowArtist(artistId).enqueue(new retrofit2.Callback<ApiResponse<FollowResult>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<FollowResult>> call,
                    retrofit2.Response<ApiResponse<FollowResult>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("unfollow failed: "
                                + response.code()));
                        return;
                    }
                    ApiResponse<FollowResult> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "unfollow failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<FollowResult>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void likeSong(int songId, Callback<LikeResult> cb) {
        api.likeSong(songId).enqueue(new retrofit2.Callback<ApiResponse<LikeResult>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<LikeResult>> call,
                    retrofit2.Response<ApiResponse<LikeResult>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("like failed: "
                                + response.code()));
                        return;
                    }
                    ApiResponse<LikeResult> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "like failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<LikeResult>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void unlikeSong(int songId, Callback<LikeResult> cb) {
        api.unlikeSong(songId).enqueue(new retrofit2.Callback<ApiResponse<LikeResult>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<LikeResult>> call,
                    retrofit2.Response<ApiResponse<LikeResult>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("unlike failed: "
                                + response.code()));
                        return;
                    }
                    ApiResponse<LikeResult> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "unlike failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<LikeResult>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void getMyLikes(Callback<List<Song>> cb) {
        api.getMyLikes().enqueue(new retrofit2.Callback<ApiResponse<List<Song>>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<List<Song>>> call,
                    retrofit2.Response<ApiResponse<List<Song>>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("likes failed: "
                                + response.code()));
                        return;
                    }
                    ApiResponse<List<Song>> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "likes failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<List<Song>>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void getFollowing(Callback<List<Artist>> cb) {
        api.getFollowing().enqueue(new retrofit2.Callback<ApiResponse<List<Artist>>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<List<Artist>>> call,
                    retrofit2.Response<ApiResponse<List<Artist>>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("following failed: "
                                + response.code()));
                        return;
                    }
                    ApiResponse<List<Artist>> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "following failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<List<Artist>>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void createPlaylist(String title, boolean isPublic,
            Callback<Playlist> cb) {
        PlaylistCreate body = new PlaylistCreate(title, isPublic);
        api.createPlaylist(body).enqueue(new retrofit2.Callback<ApiResponse<Playlist>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Playlist>> call,
                    retrofit2.Response<ApiResponse<Playlist>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("create playlist failed: "
                                + response.code()));
                        return;
                    }
                    ApiResponse<Playlist> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "create playlist failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Playlist>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void listPlaylists(Callback<List<Playlist>> cb) {
        api.listPlaylists().enqueue(new retrofit2.Callback<ApiResponse<List<Playlist>>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<List<Playlist>>> call,
                    retrofit2.Response<ApiResponse<List<Playlist>>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("playlists failed: "
                                + response.code()));
                        return;
                    }
                    ApiResponse<List<Playlist>> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "playlists failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<List<Playlist>>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void getPlaylist(int playlistId, Callback<Playlist> cb) {
        api.getPlaylist(playlistId).enqueue(new retrofit2.Callback<ApiResponse<Playlist>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Playlist>> call,
                    retrofit2.Response<ApiResponse<Playlist>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("get playlist failed: "
                                + response.code()));
                        return;
                    }
                    ApiResponse<Playlist> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "get playlist failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Playlist>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void updatePlaylist(int playlistId, String title, Boolean isPublic,
            Callback<Playlist> cb) {
        PlaylistUpdate body = new PlaylistUpdate(title, isPublic);
        api.updatePlaylist(playlistId, body).enqueue(new retrofit2.Callback<ApiResponse<Playlist>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Playlist>> call,
                    retrofit2.Response<ApiResponse<Playlist>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("update playlist failed: "
                                + response.code()));
                        return;
                    }
                    ApiResponse<Playlist> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "update playlist failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Playlist>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void addSongToPlaylist(int playlistId, int songId,
            Callback<PlaylistSong> cb) {
        api.addSongToPlaylist(playlistId, songId).enqueue(new retrofit2.Callback<ApiResponse<PlaylistSong>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<PlaylistSong>> call,
                    retrofit2.Response<ApiResponse<PlaylistSong>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "add song to playlist failed: " + response.code()));
                        return;
                    }
                    ApiResponse<PlaylistSong> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message
                                        : "add song to playlist failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<PlaylistSong>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void removeSongFromPlaylist(int playlistId, int songId,
            Callback<Void> cb) {
        api.removeSongFromPlaylist(playlistId, songId).enqueue(new retrofit2.Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Void>> call,
                    retrofit2.Response<ApiResponse<Void>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "remove song from playlist failed: " + response.code()));
                        return;
                    }
                    ApiResponse<Void> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message
                                        : "remove song from playlist failed"));
                        return;
                    }
                    cb.onSuccess(null);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Void>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }
}
