package com.oru.app;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oru.app.data.session.SessionManager;
import com.oru.app.media.PlayerService;
import com.oru.app.util.LazySession;
import com.oru.app.util.SystemBars;
import com.oru.app.util.ThemeMode;

/**
 * Application entry point. Creates the media playback notification channel and
 * wires the app-wide state.
 */
public class OruApplication extends Application {

    public static final String CHANNEL_PLAYBACK = "oru_playback";

    @Override
    public void onCreate() {
        super.onCreate();
        // The chosen theme has to be in place before the first screen inflates.
        ThemeMode.apply(this);
        createNotificationChannels();

        // The data layer builds Retrofit from the saved session, and every
        // screen asks LazySession for a service while it is being created. That
        // call throws unless something has initialised the client first, so do
        // it here, before any activity can ask for one.
        LazySession.init(this, new SessionManager(this));

        // Every screen gets its safe area from one place. Doing it here means a
        // new activity cannot forget the status bar and open with its heading
        // under the clock.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity,
                    @Nullable Bundle savedInstanceState) {
                SystemBars.fit(activity);
            }

            @Override public void onActivityStarted(@NonNull Activity activity) { }

            @Override public void onActivityResumed(@NonNull Activity activity) { }

            @Override public void onActivityPaused(@NonNull Activity activity) { }

            @Override public void onActivityStopped(@NonNull Activity activity) { }

            @Override public void onActivitySaveInstanceState(@NonNull Activity activity,
                    @NonNull Bundle outState) { }

            @Override public void onActivityDestroyed(@NonNull Activity activity) { }
        });
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
