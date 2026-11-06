package com.oru.app.data.repository;

import com.google.gson.Gson;
import com.oru.app.data.api.ApiCall;
import com.oru.app.data.api.ApiService;
import com.oru.app.data.model.*;

import java.util.List;

/** Reports and the decisions moderators make on them. */
public class ModerationRepository {

    private final ApiService api;
    private final Gson gson;

    public ModerationRepository(ApiService api, Gson gson) {
        this.api = api;
        this.gson = gson;
    }

    public void createReport(String targetType, int targetId, String reason,
            String details, Callback<Report> cb) {
        ApiCall.enqueue(
                api.createReport(new ReportCreate(targetType, targetId, reason,
                        details)),
                gson, "Couldn't send that report", cb);
    }

    /** @param status "open", "reviewed" or "actioned". */
    public void listReports(String status, Callback<List<Report>> cb) {
        ApiCall.enqueue(api.listReports(status), gson, "Couldn't load reports", cb);
    }

    public void moderationDecision(int reportId, String action, String reason,
            Callback<Void> cb) {
        ApiCall.enqueue(
                api.moderationDecision(reportId,
                        new ModerationDecision(action, reason)),
                gson, "Couldn't record that decision", cb);
    }
}
