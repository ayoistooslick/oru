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
import com.oru.app.ui.artist.ArtistActivity;
import com.oru.app.ui.auth.AuthActivity;
import com.oru.app.ui.notifications.NotificationsActivity;
import com.oru.app.ui.player.Playback;
import com.oru.app.ui.playlist.PlaylistActivity;
import com.oru.app.ui.release.ReleaseActivity;
import com.oru.app.ui.search.SearchActivity;
import com.oru.app.util.LazySession;

import java.util.Locale;

/**
 * The app's side of an oru.freebuff.app link.
 *
 * Two addresses arrive here and they mean the same thing: the verified App
 * Link `https://oru.freebuff.app/song/12` that Android hands straight to the
 * app, and the `oru://song/12` the web fires for browsers where verification
 * has not happened. The page address is translated into the same scheme form
 * before anything else, so there is one router, not two.
 *
 * It has no layout: it opens what was asked for and gets out of the way.
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
            goToMain(null);
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

        String scheme = data.getScheme() != null
                ? data.getScheme().toLowerCase(Locale.ROOT) : "";
        boolean fromWeb = "https".equals(scheme) || "http".equals(scheme);

        String[] segments = fromWeb
                ? segmentsOf(data.getPath())
                : new String[]{ data.getHost() };
        String host = segments.length > 0 ? segments[0] : null;
        if (host == null) {
            goToMain(null);
            return;
        }

        // The id, when the address names one: /song/12 or oru://song/12.
        String idPart = null;
        if (fromWeb) {
            idPart = segments.length > 1 ? segments[1] : null;
        } else {
            idPart = data.getPath() != null
                    ? data.getPath().replace("/", "").trim() : null;
        }
        Integer id = parseId(idPart);
        String query = data.getQueryParameter("q");

        switch (host) {
            case "song":
                if (id == null) {
                    goToMain(null);
                    return;
                }
                openSong(id);
                return;
            case "artist": {
                if (id == null) {
                    goToMain(null);
                    return;
                }
                Intent open = new Intent(this, ArtistActivity.class);
                open.putExtra("artistId", id);
                start(open);
                return;
            }
            case "release": {
                if (id == null) {
                    goToMain(null);
                    return;
                }
                Intent open = new Intent(this, ReleaseActivity.class);
                open.putExtra("releaseId", id);
                start(open);
                return;
            }
            // The web calls a saved playlist a library item: /library/3.
            case "playlist":
            case "library": {
                if (id == null) {
                    goToTab(R.id.libraryFragment);
                    return;
                }
                Intent open = new Intent(this, PlaylistActivity.class);
                open.putExtra("playlistId", id);
                start(open);
                return;
            }
            case "search": {
                // A search link keeps its words, so the results are already up.
                Intent open = new Intent(this, SearchActivity.class);
                if (query != null) {
                    open.putExtra("query", query);
                }
                start(open);
                return;
            }
            // Tabs of the main screen.
            case "listen":
                goToTab(R.id.homeFragment);
                return;
            case "shares":
                goToTab(R.id.sharesFragment);
                return;
            case "studio":
                goToTab(R.id.studioFragment);
                return;
            // Screens of their own.
            case "notifications":
                start(new Intent(this, NotificationsActivity.class));
                return;
            case "settings":
                start(new Intent(this, com.oru.app.ui.settings.SettingsActivity.class));
                return;
            case "moderation":
                start(new Intent(this, com.oru.app.ui.moderation.ModerationActivity.class));
                return;
            case "about":
                start(new Intent(this, com.oru.app.ui.about.AboutActivity.class));
                return;
            default:
                // Covers the front page and anything that has no screen yet.
                goToMain(null);
        }
    }

    /** "/song/12/" becomes ["song", "12"]; the leading and trailing slashes go. */
    private String[] segmentsOf(String path) {
        if (path == null) {
            return new String[0];
        }
        String trimmed = path.replaceAll("^/+", "").replaceAll("/+$", "");
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.split("/+");
    }

    /** The id in a link, or null when it carries something else. */
    private Integer parseId(String part) {
        if (part == null || part.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(part.trim());
        } catch (NumberFormatException error) {
            return null;
        }
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
        goToMain(null);
    }

    /** The main screen, asked to select a tab on the way in. */
    private void goToTab(int destination) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("tab", destination);
        goToMain(intent);
    }

    private void start(Intent intent) {
        startActivity(intent);
        finish();
    }

    /**
     * Arrives at the main screen with no leftovers: a link is the start of a
     * visit, not something to unwind back into.
     */
    private void goToMain(Intent prepared) {
        Intent intent = prepared != null
                ? prepared
                : new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
