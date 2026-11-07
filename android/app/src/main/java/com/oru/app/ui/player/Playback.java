package com.oru.app.ui.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.google.gson.Gson;
import com.oru.app.data.model.Song;

import java.util.Collections;
import java.util.List;

/**
 * Opens the player on the list the listener tapped, so next and previous move
 * through that list the way they do on the web instead of jumping 15 seconds.
 *
 * The whole list travels with the intent: the media session keeps it, so the
 * next song starts on its own when this one ends, even with the app in the
 * background or the screen off.
 */
public final class Playback {

    private static final Gson GSON = new Gson();

    private Playback() {
    }

    public static void start(Context context, List<Song> songs, int index) {
        if (context == null || songs == null || songs.isEmpty()) {
            return;
        }
        int at = Math.max(0, Math.min(index, songs.size() - 1));
        open(context, songs, at);
    }

    public static void start(Context context, Song song) {
        if (context == null || song == null) {
            return;
        }
        open(context, Collections.singletonList(song), 0);
    }

    private static void open(Context context, List<Song> songs, int index) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("songId", songs.get(index).id);
        intent.putExtra("songIndex", index);
        intent.putExtra("queue", GSON.toJson(songs));
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }
}
