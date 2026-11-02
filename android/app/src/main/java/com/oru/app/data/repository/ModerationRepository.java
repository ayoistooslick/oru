package com.oru.app.data.repository;

import com.google.gson.Gson;
import com.oru.app.data.api.ApiService;
import com.oru.app.data.model.*;

import java.util.List;

import java.io.IOException;
import retrofit2.Response;

/**
 * Moderation: report creation (any user), report list + decisions (moderator).
 */
public class ModerationRepository {

    private final ApiService api;
    private final Gson gson;

    public ModerationRepository(ApiService api, Gson gson) {
        this.api = api;
        this.gson = gson;
    }

    public void createReport(String targetType, int targetId, String reason,
            String details, Callback<Report> cb) {
        ReportCreate body = new ReportCreate(targetType, targetId, reason, details);
        api.createReport(body).enqueue(new retrofit2.Callback<ApiResponse<Report>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Report>> call,
                    retrofit2.Response<ApiResponse<Report>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("report failed: "
                                + response.code()));
                        return;
                    }
                    ApiResponse<Report> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "report failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiResponse<Report>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void listReports(Callback<List<Report>> cb) {
        api.listReports().enqueue(new retrofit2.Callback<ApiResponse<List<Report>>>() {
            @Override
            public void onResponse(
                    retrofit2.Call<ApiResponse<List<Report>>> call,
                    retrofit2.Response<ApiResponse<List<Report>>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException("reports failed: "
                                + response.code()));
                        return;
                    }
                    ApiResponse<List<Report>> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message : "reports failed"));
                        return;
                    }
                    cb.onSuccess(r.data);
                } catch (Exception e) {
                    cb.onFailure(e);
                }
            }

            @Override
            public void onFailure(
                    retrofit2.Call<ApiResponse<List<Report>>> call,
                    Throwable t) {
                cb.onFailure(t);
            }
        });
    }

    public void moderationDecision(int reportId, String action, String reason,
            Callback<Void> cb) {
        ModerationDecision body = new ModerationDecision(action, reason);
        api.moderationDecision(reportId, body).enqueue(new retrofit2.Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(retrofit2.Call<ApiResponse<Void>> call,
                    retrofit2.Response<ApiResponse<Void>> response) {
                try {
                    if (!response.isSuccessful()) {
                        cb.onFailure(new IOException(
                                "moderation decision failed: " + response.code()));
                        return;
                    }
                    ApiResponse<Void> r = response.body();
                    if (r == null || !r.isSuccess()) {
                        cb.onFailure(new IOException(
                                r != null ? r.error.message
                                        : "moderation decision failed"));
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
