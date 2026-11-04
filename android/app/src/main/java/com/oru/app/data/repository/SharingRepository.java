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

/**
 * Listener sharing: songs waiting on an artist's approval, links, and songs
 * shared from outside ORU.
 */
public class SharingRepository {

    private final ApiService api;
    private final Gson gson;

    public SharingRepository(ApiService api, Gson gson) {
        this.api = api;
        this.gson = gson;
    }

    /** Songs I shared, with the status their artist gave them. */
    public void listSharedSongs(Callback<List<Song>> cb) {
        ApiCall.enqueue(api.listSharedSongs(), gson,
                "Couldn't load the songs you shared", cb);
    }

    public void revokeShare(int songId, Callback<Void> cb) {
        ApiCall.enqueue(api.revokeShare(songId), gson,
                "Couldn't withdraw that share", cb);
    }

    /** Songs shared with me as an artist, waiting for my decision. */
    public void listPendingShares(Callback<List<Song>> cb) {
        ApiCall.enqueue(api.listPendingShares(), gson,
                "Couldn't load share requests", cb);
    }

    /** Shares a song that is already yours to upload, credited to its artist. */
    public void shareSong(int artistId, String title, String description,
            List<String> genres, String sourceUrl, long durationMs, File audio,
            String audioMime, File cover, String coverMime,
            Callback<Song> cb) {
        List<RequestBody> genreParts = new ArrayList<>();
        if (genres != null) {
            for (String genre : genres) {
                if (genre != null && !genre.trim().isEmpty()) {
                    genreParts.add(Uploads.text(genre.trim()));
                }
            }
        }
        ApiCall.enqueue(api.shareSong(Uploads.number(artistId),
                Uploads.text(title), Uploads.text(description), genreParts,
                Uploads.text(sourceUrl), Uploads.number(durationMs),
                Uploads.file("audio", audio.getName(), audioMime, audio),
                cover != null
                        ? Uploads.file("cover", cover.getName(), coverMime, cover)
                        : null),
                gson, "Couldn't share that song", cb);
    }

    /** Shares a song that lives somewhere else, by an artist not on ORU yet. */
    public void createExternalShare(String title, String artistName, String url,
            String note, Callback<ExternalShare> cb) {
        ApiCall.enqueue(
                api.createExternalShare(title, artistName, url,
                        note != null ? note : ""),
                gson, "Couldn't share that link", cb);
    }

    public void listExternalShares(Callback<List<ExternalShare>> cb) {
        ApiCall.enqueue(api.listExternalShares(), gson,
                "Couldn't load shared links", cb);
    }

    public void revokeExternalShare(int shareId, Callback<Void> cb) {
        ApiCall.enqueue(api.revokeExternalShare(shareId), gson,
                "Couldn't withdraw that link", cb);
    }

    public void shareDecision(int songId, String action, Callback<Void> cb) {
        ApiCall.enqueue(api.shareDecision(songId, new ShareDecision(action)),
                gson, "Couldn't record that decision", cb);
    }
}
