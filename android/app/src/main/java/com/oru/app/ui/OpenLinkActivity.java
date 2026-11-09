package com.oru.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.oru.app.R;
import com.oru.app.data.api.ApiCall;
import com.oru.app.data.api.ApiService;
import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.session.SessionManager;
import com.oru.app.ui.auth.AuthActivity;
import com.oru.app.ui.artist.ArtistActivity;
import com.oru.app.ui.player.Playback;
import com.oru.app.ui.playlist.PlaylistActivity;
import com.oru.app.ui.release.ReleaseActivity;
import com.oru.app.util.LazySession;

/**
 * The app's side of a shared link, reached through oru://song/12,
 * oru://artist/4, oru://release/7 or oru://playlist/3.
 *
 * Every web page that names something fires an Android intent URL at this
 * activity, so a listener with the app installed lands in the app instead of
 * the browser. It has no layout: it either opens what the link names or sends
 * the listener to sign in first, and the link survives that detour.
 */
public class OpenLinkActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        if (data == null) {
            goToMain();
            return;
        }

        SessionManager session = new SessionManager(this);
        if (!session.isSignedIn()) {
            // MainActivity opens this again once there is an account, whichever
            // way the listener signed in.
            session.setPendingLink(data.toString());
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        String host = data.getHost();
        String idPart = data.getPath() != null ? data.getPath().replace("/", "").trim() : "";
        int id;
        try {
            id = Integer.parseInt(idPart);
        } catch (NumberFormatException error) {
            goToMain();
            return;
        }

        if ("song".equals(host)) {
            openSong(id);
        } else if ("artist".equals(host)) {
            open(ArtistActivity.class, "artistId", id);
        } else if ("release".equals(host)) {
            open(ReleaseActivity.class, "releaseId", id);
        } else if ("playlist".equals(host)) {
            open(PlaylistActivity.class, "playlistId", id);
        } else {
            goToMain();
        }
    }

    /** The other three screens all take the id their activity was built with. */
    private void open(Class<? extends AppCompatActivity> screen, String idKey, int id) {
        Intent intent = new Intent(this, screen);
        intent.putExtra(idKey, id);
        startActivity(intent);
        finish();
    }

    private void openSong(int songId) {
        ApiService api = LazySession.getApiService();
        ApiCall.enqueue(api.getSong(songId), LazySession.getGson(),
                getString(R.string.song_unavailable), new Callback<Song>() {
                    @Override
                    public void onSuccess(Song song) {
                        runOnUiThread(() -> {
                            if (song == null) {
                                unavailable();
                                return;
                            }
                            Playback.start(OpenLinkActivity.this, song);
                            finish();
                        });
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        runOnUiThread(OpenLinkActivity.this::unavailable);
                    }
                });
    }

    private void unavailable() {
        Toast.makeText(this, R.string.song_unavailable, Toast.LENGTH_LONG).show();
        goToMain();
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
