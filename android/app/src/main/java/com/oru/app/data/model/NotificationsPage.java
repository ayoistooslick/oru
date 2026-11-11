package com.oru.app.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * The bell's payload: the newest drops plus the number still unread, so one
 * request draws the whole screen.
 */
public class NotificationsPage {

    @SerializedName("items")
    public List<DropNotification> items = new ArrayList<>();

    @SerializedName("unread_count")
    public int unreadCount;

    @SerializedName("limit")
    public int limit;

    @SerializedName("offset")
    public int offset;

    /** The count on its own, for a badge without the list. */
    public static class Unread {
        @SerializedName("unread")
        public int unread;
    }

    /** What "mark all read" reports back. */
    public static class Marked {
        @SerializedName("marked")
        public int marked;

        @SerializedName("unread_count")
        public int unreadCount;
    }

    /** A single read receipt: the updated row and the new count. */
    public static class Read {
        @SerializedName("notification")
        public DropNotification notification;

        @SerializedName("unread_count")
        public int unreadCount;
    }
}
