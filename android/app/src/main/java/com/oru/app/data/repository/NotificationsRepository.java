package com.oru.app.data.repository;

import com.google.gson.Gson;
import com.oru.app.data.api.ApiCall;
import com.oru.app.data.api.ApiService;
import com.oru.app.data.model.NotificationPreference;
import com.oru.app.data.model.NotificationsPage;

/**
 * The bell: new-drop notices for the artists this listener follows.
 *
 * Reading is quiet — a failure only means an empty list, never a blocking
 * error — because a drops screen is never the reason someone opened the app.
 */
public class NotificationsRepository {

    private final ApiService api;
    private final Gson gson;

    public NotificationsRepository(ApiService api, Gson gson) {
        this.api = api;
        this.gson = gson;
    }

    public void list(Callback<NotificationsPage> cb) {
        ApiCall.enqueuePlain(api.listNotifications(50), gson,
                "Couldn't load your drops", cb);
    }

    public void unread(Callback<NotificationsPage.Unread> cb) {
        ApiCall.enqueuePlain(api.unreadNotifications(), gson,
                "Couldn't load your drops", cb);
    }

    public void markRead(int notificationId, Callback<NotificationsPage.Read> cb) {
        ApiCall.enqueuePlain(api.readNotification(notificationId), gson,
                "Couldn't mark that as read", cb);
    }

    public void markAllRead(Callback<NotificationsPage.Marked> cb) {
        ApiCall.enqueuePlain(api.readAllNotifications(), gson,
                "Couldn't mark those as read", cb);
    }

    /** Silences future drops without unfollowing anyone. */
    public void setDropsEnabled(boolean enabled,
            Callback<NotificationPreference> cb) {
        ApiCall.enqueuePlain(
                api.setDropNotifications(new NotificationPreference(enabled)), gson,
                "Couldn't change that setting", cb);
    }
}
