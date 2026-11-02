package com.oru.app.data.repository;

import com.google.gson.Gson;
import com.oru.app.data.api.ApiService;
import com.oru.app.data.model.*;

import java.util.List;

import java.io.IOException;
import retrofit2.Response;

/**
 * Sharing operations: create/share links, external shares, pending shares,
 * share decisions.
 */
public class SharingRepository {

    private final ApiService api;
    private final Gson gson;

    public SharingRepository(ApiService api, Gson gson) {
        this.api = api;
        this.gson = gson;
    }

    public void createShareLink(int songId, Callback<ShareLink> cb) {
        ShareCreate body = new ShareCreate(songId);
        api.createShareLink(body).enqueue(new retrofit2.Callback<ApiResponse<ShareLink>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<ShareLink>> call,
                    retrofit2.Response<ApiResponse<ShareLink>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "create share link failed: " + response.code()));
                        return;
                    }
                    ApiResponse<ShareLink> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message
                                        : "create share link failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<ShareLink>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void listSharedSongs(Callback<List<SharedSong>> cb) {
        api.listSharedSongs().enqueue(new retrofit2.Callback<ApiResponse<List<SharedSong>>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<List<SharedSong>>> call,
                    retrofit2.Response<ApiResponse<List<SharedSong>>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "shared songs failed: " + response.code()));
                        return;
                    }
                    ApiResponse<List<SharedSong>> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "shared songs failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(
                    retrofit2.Call<ApiResponse<List<SharedSong>>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void revokeShare(int songId, Callback<Void> cb) {
        api.revokeShare(songId).enqueue(new retrofit2.Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Void>> call,
                    retrofit2.Response<ApiResponse<Void>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "revoke share failed: " + response.code()));
                        return;
                    }
                    ApiResponse<Void> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "revoke share failed"));
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

    public void listPendingShares(Callback<List<PendingShare>> cb) {
        api.listPendingShares().enqueue(new retrofit2.Callback<ApiResponse<List<PendingShare>>>() {
            @Override
            public void onResponse(
                    retrofit2.Call<ApiResponse<List<PendingShare>>> call,
                    retrofit2.Response<ApiResponse<List<PendingShare>>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "pending shares failed: " + response.code()));
                        return;
                    }
                    ApiResponse<List<PendingShare>> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "pending shares failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(
                    retrofit2.Call<ApiResponse<List<PendingShare>>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void createExternalShare(int songId, String platform,
            Callback<ExternalShare> cb) {
        ExternalShareCreate body = new ExternalShareCreate(songId, platform);
        api.createExternalShare(body).enqueue(new retrofit2.Callback<ApiResponse<ExternalShare>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<ExternalShare>> call,
                    retrofit2.Response<ApiResponse<ExternalShare>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "create external share failed: " + response.code()));
                        return;
                    }
                    ApiResponse<ExternalShare> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message
                                        : "create external share failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<ExternalShare>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void listExternalShares(Callback<List<ExternalShare>> cb) {
        api.listExternalShares().enqueue(new retrofit2.Callback<ApiResponse<List<ExternalShare>>>() {
            @Override
            public void onResponse(
                    retrofit2.Call<ApiResponse<List<ExternalShare>>> call,
                    retrofit2.Response<ApiResponse<List<ExternalShare>>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "external shares failed: " + response.code()));
                        return;
                    }
                    ApiResponse<List<ExternalShare>> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "external shares failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(
                    retrofit2.Call<ApiResponse<List<ExternalShare>>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void revokeExternalShare(int shareId, Callback<Void> cb) {
        api.revokeExternalShare(shareId).enqueue(new retrofit2.Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Void>> call,
                    retrofit2.Response<ApiResponse<Void>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "revoke external share failed: " + response.code()));
                        return;
                    }
                    ApiResponse<Void> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message
                                        : "revoke external share failed"));
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

    public void shareDecision(int songId, String action, Callback<Void> cb) {
        ShareDecision body = new ShareDecision(action);
        api.shareDecision(songId, body).enqueue(new retrofit2.Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Void>> call,
                    retrofit2.Response<ApiResponse<Void>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "share decision failed: " + response.code()));
                        return;
                    }
                    ApiResponse<Void> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "share decision failed"));
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
