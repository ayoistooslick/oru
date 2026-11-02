package com.oru.app.data.repository;

import com.google.gson.Gson;
import com.oru.app.data.api.ApiService;
import com.oru.app.data.model.*;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import com.oru.app.data.repository.Callback;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Music operations: songs, releases, artist songs/releases, discover, rankings,
 * listen tracking, playback (audio/cover streams), song CRUD for artists.
 */
public class MusicRepository {

    private final ApiService api;
    private final Gson gson;

    public MusicRepository(ApiService api, Gson gson) {
        this.api = api;
        this.gson = gson;
    }

    public void getDiscover(Callback<DiscoverResult> cb) {
        api.discover().enqueue(new retrofit2.Callback<ApiResponse<DiscoverResult>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<DiscoverResult>> call,
                    retrofit2.Response<ApiResponse<DiscoverResult>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("discover failed: "
                                + response.code()));
                        return;
                    }
                    ApiResponse<DiscoverResult> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "discover failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<DiscoverResult>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void listSongs(int page, int limit, Callback<List<Song>> cb) {
        api.listSongs(page, limit).enqueue(new retrofit2.Callback<ApiResponse<List<Song>>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<List<Song>>> call,
                    retrofit2.Response<ApiResponse<List<Song>>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("list songs failed: "
                                + response.code()));
                        return;
                    }
                    ApiResponse<List<Song>> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "list songs failed"));
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

    public void getSong(int songId, Callback<Song> cb) {
        api.getSong(songId).enqueue(new retrofit2.Callback<ApiResponse<Song>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Song>> call,
                    retrofit2.Response<ApiResponse<Song>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("get song failed: "
                                + response.code()));
                        return;
                    }
                    ApiResponse<Song> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "get song failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Song>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }


    public void getArtist(int artistId, Callback<Artist> cb) {
        api.getArtist(artistId).enqueue(new retrofit2.Callback<ApiResponse<Artist>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Artist>> call,
                    retrofit2.Response<ApiResponse<Artist>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().data != null) {
                    cb.onSuccess(response.body().data);
                } else {
                    cb.onFailure(describeResponse(response));
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Artist>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void getSongAudio(int songId, Callback<ResponseBody> cb) {
        api.getSongAudio(songId).enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<ResponseBody> call,
                    retrofit2.Response<ResponseBody> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("audio failed: "
                                + response.code()));
                        return;
                    }
                    cb.onSuccess(response.body());
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ResponseBody> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void getSongCover(int songId, Callback<ResponseBody> cb) {
        api.getSongCover(songId).enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<ResponseBody> call,
                    retrofit2.Response<ResponseBody> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("cover failed: "
                                + response.code()));
                        return;
                    }
                    cb.onSuccess(response.body());
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ResponseBody> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void getArtistSongs(int artistId, Callback<List<Song>> cb) {
        api.getArtistSongs(artistId).enqueue(new retrofit2.Callback<ApiResponse<List<Song>>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<List<Song>>> call,
                    retrofit2.Response<ApiResponse<List<Song>>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "artist songs failed: " + response.code()));
                        return;
                    }
                    ApiResponse<List<Song>> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "artist songs failed"));
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

    public void getArtistReleases(int artistId, Callback<List<Release>> cb) {
        api.getArtistReleases(artistId).enqueue(new retrofit2.Callback<ApiResponse<List<Release>>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<List<Release>>> call,
                    retrofit2.Response<ApiResponse<List<Release>>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "artist releases failed: " + response.code()));
                        return;
                    }
                    ApiResponse<List<Release>> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "artist releases failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<List<Release>>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void getRelease(int releaseId, Callback<Release> cb) {
        api.getRelease(releaseId).enqueue(new retrofit2.Callback<ApiResponse<Release>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Release>> call,
                    retrofit2.Response<ApiResponse<Release>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "get release failed: " + response.code()));
                        return;
                    }
                    ApiResponse<Release> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "get release failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Release>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void getFeedFollowing(Callback<List<FeedItem>> cb) {
        api.feedFollowing().enqueue(new retrofit2.Callback<ApiResponse<List<FeedItem>>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<List<FeedItem>>> call,
                    retrofit2.Response<ApiResponse<List<FeedItem>>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "feed failed: " + response.code()));
                        return;
                    }
                    ApiResponse<List<FeedItem>> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "feed failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<List<FeedItem>>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void recordListen(int songId, int listenedMs, Callback<ListenRecord> cb) {
        ListeningCreate body = new ListeningCreate(listenedMs);
        api.recordListen(songId, body).enqueue(new retrofit2.Callback<ApiResponse<ListenRecord>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<ListenRecord>> call,
                    retrofit2.Response<ApiResponse<ListenRecord>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "record listen failed: " + response.code()));
                        return;
                    }
                    ApiResponse<ListenRecord> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "record listen failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<ListenRecord>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void getMySongs(Callback<List<Song>> cb) {
        api.getMySongs().enqueue(new retrofit2.Callback<ApiResponse<List<Song>>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<List<Song>>> call,
                    retrofit2.Response<ApiResponse<List<Song>>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "my songs failed: " + response.code()));
                        return;
                    }
                    ApiResponse<List<Song>> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "my songs failed"));
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

    public void createSong(String title, String description, String coverUrl,
            String audioUrl, Callback<Song> cb) {
        SongCreate body = new SongCreate(title, description, coverUrl, audioUrl);
        api.createSong(body).enqueue(new retrofit2.Callback<ApiResponse<Song>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Song>> call,
                    retrofit2.Response<ApiResponse<Song>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "create song failed: " + response.code()));
                        return;
                    }
                    ApiResponse<Song> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "create song failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Song>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void publishSong(int songId, Callback<Song> cb) {
        api.publishSong(songId).enqueue(new retrofit2.Callback<ApiResponse<Song>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Song>> call,
                    retrofit2.Response<ApiResponse<Song>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "publish song failed: " + response.code()));
                        return;
                    }
                    ApiResponse<Song> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "publish song failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Song>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void deleteSong(int songId, Callback<Void> cb) {
        api.deleteSong(songId).enqueue(new retrofit2.Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Void>> call,
                    retrofit2.Response<ApiResponse<Void>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "delete song failed: " + response.code()));
                        return;
                    }
                    ApiResponse<Void> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "delete song failed"));
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

    public void listGenres(Callback<List<Genre>> cb) {
        api.listGenres().enqueue(new retrofit2.Callback<ApiResponse<List<Genre>>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<List<Genre>>> call,
                    retrofit2.Response<ApiResponse<List<Genre>>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "genres failed: " + response.code()));
                        return;
                    }
                    ApiResponse<List<Genre>> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "genres failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<List<Genre>>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void weeklyRankings(Callback<List<RankingEntry>> cb) {
        api.weeklyRankings().enqueue(new retrofit2.Callback<ApiResponse<List<RankingEntry>>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<List<RankingEntry>>> call,
                    retrofit2.Response<ApiResponse<List<RankingEntry>>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "rankings failed: " + response.code()));
                        return;
                    }
                    ApiResponse<List<RankingEntry>> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "rankings failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<List<RankingEntry>>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }
    private Throwable describeResponse(retrofit2.Response<?> response) {
        if (response == null) return new java.io.IOException("null response");
        if (response.isSuccessful()) return null;
        return new java.io.IOException("HTTP " + response.code());
    }

}
