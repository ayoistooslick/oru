package com.oru.app.ui.components;

import android.content.Context;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.oru.app.data.model.Playlist;
import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.CommunityRepository;
import com.oru.app.util.LazySession;

import java.util.ArrayList;
import java.util.List;

/** The two playlist dialogs the lists need: make one, or file a song into one. */
public final class PlaylistPicker {

    public interface Created {
        void onCreated(Playlist playlist);
    }

    private PlaylistPicker() {
    }

    private static CommunityRepository repo() {
        return new CommunityRepository(LazySession.getApiService(),
                LazySession.getGson());
    }

    /** Offers the listener's playlists for this song, plus a way to make one. */
    public static void addTo(Context context, Song song) {
        CommunityRepository playlists = repo();
        playlists.listPlaylists(new Callback<List<Playlist>>() {
            @Override
            public void onSuccess(List<Playlist> items) {
                List<String> labels = new ArrayList<>();
                labels.add("New playlist…");
                if (items != null) {
                    for (Playlist playlist : items) {
                        labels.add(playlist.title + " · " + playlist.songCount()
                                + " songs");
                    }
                }
                new AlertDialog.Builder(context)
                        .setTitle("Add " + song.title)
                        .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                            if (which == 0) {
                                create(context, playlist -> playlists
                                        .addSongToPlaylist(playlist.id, song.id,
                                                added(context, playlist)));
                            } else {
                                add(context, playlists, items.get(which - 1),
                                        song);
                            }
                        })
                        .setNegativeButton(com.oru.app.R.string.cancel, null)
                        .show();
            }

            @Override
            public void onFailure(Throwable error) {
                Toast.makeText(context, UiErrors.message(context, error),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void add(Context context, CommunityRepository playlists,
            Playlist playlist, Song song) {
        playlists.addSongToPlaylist(playlist.id, song.id, added(context, playlist));
    }

    private static Callback<Void> added(Context context, Playlist playlist) {
        return new Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                Toast.makeText(context,
                        "Added to " + playlist.title, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Throwable error) {
                Toast.makeText(context, UiErrors.message(context, error),
                        Toast.LENGTH_LONG).show();
            }
        };
    }

    /** Asks for a title and a visibility, then creates the playlist. */
    public static void create(Context context, Created created) {
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(pad, pad, pad, 0);

        final EditText title = new EditText(context);
        title.setHint("Playlist name");
        title.setSingleLine(true);
        form.addView(title);

        final CheckBox isPublic = new CheckBox(context);
        isPublic.setText("Anyone can see it");
        isPublic.setChecked(true);
        form.addView(isPublic);

        new AlertDialog.Builder(context)
                .setTitle("New playlist")
                .setView(form)
                .setNegativeButton(com.oru.app.R.string.cancel, null)
                .setPositiveButton(com.oru.app.R.string.save, (dialog, which) -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    repo().createPlaylist(name, isPublic.isChecked(),
                            new Callback<Playlist>() {
                                @Override
                                public void onSuccess(Playlist playlist) {
                                    if (created != null) {
                                        created.onCreated(playlist);
                                    }
                                }

                                @Override
                                public void onFailure(Throwable error) {
                                    Toast.makeText(context,
                                            UiErrors.message(context, error),
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .show();
    }
}
