package com.oru.app.data.repository;

import com.google.gson.Gson;
import com.oru.app.data.api.ApiCall;
import com.oru.app.data.api.ApiService;
import com.oru.app.data.api.Uploads;
import com.oru.app.data.model.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;

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
        ApiCall.enqueue(api.discover(), gson, "Couldn't load the discover list", cb);
    }

    /** Searches published songs; an empty query returns the newest songs. */
    public void listSongs(String query, int limit, int offset,
            Callback<List<Song>> cb) {
        String q = query == null || query.isEmpty() ? null : query;
        ApiCall.enqueue(api.listSongs(q, limit, offset), gson,
                "Couldn't load songs", cb);
    }

    public void getSong(int songId, Callback<Song> cb) {
        ApiCall.enqueue(api.getSong(songId), gson, "Couldn't load this song", cb);
    }

    /** Artists matching a search, used when crediting a shared song. */
    public void searchArtists(String query, Callback<List<Artist>> cb) {
        ApiCall.enqueue(api.listArtists(query, 20), gson,
                "Couldn't search artists", cb);
    }

    public void getArtist(int artistId, Callback<Artist> cb) {
        ApiCall.enqueue(api.getArtist(artistId), gson,
                "Couldn't load this artist", cb);
    }

    public void getSongsByArtist(int artistId, int limit, int offset,
            Callback<List<Song>> cb) {
        ApiCall.enqueue(api.getArtistSongs(artistId, limit, offset), gson,
                "Couldn't load this artist's songs", cb);
    }

    public void getReleasesByArtist(int artistId, int limit, int offset,
            Callback<List<Release>> cb) {
        ApiCall.enqueue(api.getArtistReleases(artistId, limit, offset), gson,
                "Couldn't load this artist's releases", cb);
    }

    public void getRelease(int releaseId, Callback<Release> cb) {
        ApiCall.enqueue(api.getRelease(releaseId), gson,
                "Couldn't load this release", cb);
    }

    /** Groups my songs into a single, EP or album. It starts out as a draft. */
    public void createRelease(String title, String kind, List<Integer> songIds,
            Callback<Release> cb) {
        ApiCall.enqueue(api.createRelease(new ReleaseCreate(title, kind, songIds)),
                gson, "Couldn't create this release", cb);
    }

    public void publishRelease(int releaseId, Callback<Release> cb) {
        ApiCall.enqueue(api.publishRelease(releaseId), gson,
                "Couldn't publish this release", cb);
    }

    public void getMyReleases(Callback<List<Release>> cb) {
        ApiCall.enqueue(api.getMyReleases(), gson,
                "Couldn't load your releases", cb);
    }

    /** Songs where this artist is only a feature. */
    public void getAppearsOn(int artistId, Callback<List<Song>> cb) {
        ApiCall.enqueue(api.getArtistAppearsOn(artistId), gson,
                "Couldn't load this artist's features", cb);
    }

    /** My own numbers: published songs, likes and qualified listens. */
    public void getArtistStats(Callback<ArtistStats> cb) {
        ApiCall.enqueue(api.getArtistStats(), gson,
                "Couldn't load your numbers", cb);
    }

    /** A short post on my artist profile, shown to listeners. */
    public void createPost(String body, Callback<ArtistPost> cb) {
        ApiCall.enqueue(api.createArtistPost(new ArtistPostCreate(body)), gson,
                "Couldn't publish that post", cb);
    }

    public void getMyPosts(Callback<List<ArtistPost>> cb) {
        ApiCall.enqueue(api.getMyPosts(), gson,
                "Couldn't load your posts", cb);
    }

    public void getPostsByArtist(int artistId, Callback<List<ArtistPost>> cb) {
        ApiCall.enqueue(api.getArtistPosts(artistId), gson,
                "Couldn't load this artist's posts", cb);
    }

    /** Songs by the artists the signed-in listener follows, newest first. */
    public void getFeedFollowing(Callback<List<Song>> cb) {
        ApiCall.enqueue(api.feedFollowing(), gson,
                "Couldn't load your feed", cb);
    }

    public void recordListen(int songId, int listenedMs,
            Callback<ListenRecord> cb) {
        ApiCall.enqueue(api.recordListen(songId, new ListeningCreate(listenedMs)),
                gson, "Couldn't record that listen", cb);
    }

    /** Uploads a track: the audio file, optional artwork, and the fields the
     * API's multipart form expects. */
    public void uploadSong(String title, String description, List<String> genres,
            List<Integer> featuredIds, long durationMs, File audio,
            String audioMime, File cover, String coverMime, Callback<Song> cb) {
        List<RequestBody> genreParts = new ArrayList<>();
        if (genres != null) {
            for (String genre : genres) {
                if (genre != null && !genre.trim().isEmpty()) {
                    genreParts.add(Uploads.text(genre.trim()));
                }
            }
        }
        List<RequestBody> featuredParts = new ArrayList<>();
        if (featuredIds != null) {
            for (Integer id : featuredIds) {
                if (id != null && id > 0) {
                    featuredParts.add(Uploads.number(id));
                }
            }
        }
        // The API refuses a length of zero, and some files cannot be measured
        // on the phone. The web omits it in that case, so this does too.
        RequestBody length = durationMs > 0 ? Uploads.number(durationMs) : null;
        ApiCall.enqueue(api.createSong(Uploads.text(title),
                Uploads.text(description), genreParts, featuredParts,
                length,
                Uploads.file("audio", audio.getName(), audioMime, audio),
                cover != null
                        ? Uploads.file("cover", cover.getName(), coverMime, cover)
                        : null),
                gson, "Couldn't upload this song", cb);
    }

    public void getMySongs(Callback<List<Song>> cb) {
        ApiCall.enqueue(api.getMySongs(), gson, "Couldn't load your songs", cb);
    }

    public void publishSong(int songId, Callback<Song> cb) {
        ApiCall.enqueue(api.publishSong(songId), gson,
                "Couldn't publish this song", cb);
    }

    public void unpublishSong(int songId, Callback<Song> cb) {
        ApiCall.enqueue(api.unpublishSong(songId), gson,
                "Couldn't unpublish this song", cb);
    }

    public void deleteSong(int songId, Callback<Void> cb) {
        ApiCall.enqueue(api.deleteSong(songId), gson,
                "Couldn't delete this song", cb);
    }

    public void listGenres(Callback<List<Genre>> cb) {
        ApiCall.enqueue(api.listGenres(), gson, "Couldn't load genres", cb);
    }

    public void weeklyRankings(Callback<List<RankingEntry>> cb) {
        ApiCall.enqueue(api.weeklyRankings(), gson,
                "Couldn't load this week's rankings", cb);
    }

    public void getSongAudio(int songId, Callback<ResponseBody> cb) {
        api.getSongAudio(songId).enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<ResponseBody> call,
                    retrofit2.Response<ResponseBody> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    cb.onFailure(ApiCall.error(gson, response,
                            "Couldn't load the audio for this song"));
                    return;
                }
                cb.onSuccess(response.body());
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
                if (!response.isSuccessful() || response.body() == null) {
                    cb.onFailure(ApiCall.error(gson, response,
                            "Couldn't load the cover for this song"));
                    return;
                }
                cb.onSuccess(response.body());
            }

            @Override
            public void onFailure(retrofit2.Call<ResponseBody> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }
}
