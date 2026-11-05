package com.oru.app.ui.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.oru.app.data.model.Song;

import java.util.List;

/**
 * Opens the player on the list the listener tapped, so next and previous move
 * through that list the way they do on the web instead of jumping 15 seconds.
 */
public final class Playback {

    private Playback() {
    }

    public static void start(Context context, List<Song> songs, int index) {
        if (context == null || songs == null || songs.isEmpty()) {
            return;
        }
        int at = Math.max(0, Math.min(index, songs.size() - 1));
        int[] ids = new int[songs.size()];
        for (int i = 0; i < songs.size(); i++) {
            ids[i] = songs.get(i).id;
        }
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("songId", songs.get(at).id);
        intent.putExtra("songIds", ids);
        intent.putExtra("songIndex", at);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public static void start(Context context, Song song) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra("songId", song.id);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }
}
