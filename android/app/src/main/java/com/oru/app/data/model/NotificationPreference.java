package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * The new-drop switch.
 *
 * Sent as {"enabled": true} and answered as {"notify_new_drops": true}; one
 * object carries both shapes so the request and the reply cannot drift apart.
 * Following an artist is the opt-in — this only silences future notices and
 * never unfollows anyone, matching the web's switch.
 */
public class NotificationPreference {

    @SerializedName("enabled")
    public boolean enabled;

    @SerializedName("notify_new_drops")
    public boolean notifyNewDrops;

    public NotificationPreference(boolean enabled) {
        this.enabled = enabled;
        this.notifyNewDrops = enabled;
    }
}
