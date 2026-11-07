package com.oru.app.util;

/**
 * What is playing right now, in plain words.
 *
 * The media session normally carries the title, artist and artwork itself, but
 * the system also reads them through the platform session, which can lag or
 * drop them on some phones and API levels. Keeping a copy here means the mini
 * player and the notification can always name the song, even when the session's
 * own metadata arrives empty.
 */
public final class NowPlaying {

    private static volatile String title;
    private static volatile String artist;
    private static volatile String coverUrl;
    private static volatile int songId;

    private NowPlaying() {
    }

    public static void remember(int id, String songTitle, String songArtist,
            String cover) {
        songId = id;
        title = songTitle;
        artist = songArtist;
        coverUrl = cover;
    }

    public static int songId() {
        return songId;
    }

    public static String title() {
        return title;
    }

    public static String artist() {
        return artist;
    }

    public static String coverUrl() {
        return coverUrl;
    }
}
