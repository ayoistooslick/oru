package com.oru.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.oru.app.data.session.SessionManager;
import com.oru.app.media.PlayerService;
import com.oru.app.util.LazySession;

/**
 * Application entry point. Creates the media playback notification channel and
 * wires the app-wide state.
 */
public class OruApplication extends Application {

    public static final String CHANNEL_PLAYBACK = "oru_playback";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();

        // The data layer builds Retrofit from the saved session, and every
        // screen asks LazySession for a service while it is being created. That
        // call throws unless something has initialised the client first, so do
        // it here, before any activity can ask for one.
        LazySession.init(this, new SessionManager(this));
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel playback =
                    new NotificationChannel(
                            CHANNEL_PLAYBACK,
                            "Playback",
                            NotificationManager.IMPORTANCE_LOW);
            playback.setDescription("Now playing controls");
            playback.setShowBadge(false);

            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(playback);
            }
        }
    }
}
