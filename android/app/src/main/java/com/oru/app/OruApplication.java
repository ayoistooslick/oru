package com.oru.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.oru.app.media.PlayerService;

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
