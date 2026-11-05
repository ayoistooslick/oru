package com.oru.app.ui.components;

import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.oru.app.R;
import com.oru.app.data.offline.Downloads;
import com.oru.app.util.MediaUrl;

/**
 * One place that turns an API media path into artwork on screen.
 *
 * Lists used to leave their artwork empty, and a null or unreachable cover left
 * a blank square; every row now shows the image, the saved copy when the song is
 * downloaded, and a placeholder otherwise.
 */
public final class Covers {

    private Covers() {
    }

    public static void load(ImageView view, String path) {
        load(view, path, R.drawable.ic_music);
    }

    /** Covers a song or release: falls back to the song's saved artwork. */
    public static void loadSongCover(ImageView view, int songId, String path) {
        if (view == null) {
            return;
        }
        java.io.File saved = Downloads.localCover(view.getContext(), songId);
        if (saved != null) {
            view.setVisibility(View.VISIBLE);
            Glide.with(view.getContext()).load(saved)
                    .placeholder(R.drawable.ic_music)
                    .error(R.drawable.ic_music)
                    .into(view);
            return;
        }
        load(view, path, R.drawable.ic_music);
    }

    public static void loadAvatar(ImageView view, String path) {
        load(view, path, R.drawable.ic_artist);
    }

    public static void load(ImageView view, String path, int placeholder) {
        if (view == null) {
            return;
        }
        String url = MediaUrl.of(path);
        view.setVisibility(View.VISIBLE);
        if (url == null) {
            Glide.with(view.getContext()).clear(view);
            view.setImageResource(placeholder);
            return;
        }
        Glide.with(view.getContext())
                .load(url)
                .placeholder(placeholder)
                .error(placeholder)
                .transition(DrawableTransitionOptions.withCrossFade(180))
                .into(view);
    }
}
