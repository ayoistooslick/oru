package com.oru.app.data.repository;

import com.google.gson.Gson;
import com.oru.app.data.api.ApiCall;
import com.oru.app.data.api.ApiService;
import com.oru.app.data.model.*;

import java.util.List;

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

    public void createExternalShare(ExternalShareCreate body,
            Callback<ExternalShare> cb) {
        ApiCall.enqueue(api.createExternalShare(body), gson,
                "Couldn't share that link", cb);
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
