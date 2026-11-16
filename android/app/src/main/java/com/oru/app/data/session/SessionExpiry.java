package com.oru.app.data.session;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * What happens when the server rejects the saved token.
 *
 * Before this, a 401 surfaced as a page-level error ("Your sign-in token is
 * invalid or expired") with a Try again button that retried the same dead token.
 * The saved session was still on disk, so the app also kept believing it was
 * signed in. Here the session is cleared once, and the sign-in screen is opened
 * with a note saying why. That is the only screen where the problem can be
 * fixed.
 */
public final class SessionExpiry {

    /** A burst of parallel requests must not queue three sign-in screens. */
    private static final long QUIET_MS = 20_000;
    private static volatile long lastHandled;

    private SessionExpiry() {
    }

    public static void onUnauthorized(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastHandled < QUIET_MS) {
            return;
        }
        lastHandled = now;
        Context app = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() -> {
            SessionManager session = new SessionManager(app);
            if (!session.isSignedIn()) {
                return;
            }
            session.clearSession();
            Intent intent = new Intent(app,
                    com.oru.app.ui.auth.AuthActivity.class);
            intent.putExtra("session_ended", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            app.startActivity(intent);
        });
    }
}
