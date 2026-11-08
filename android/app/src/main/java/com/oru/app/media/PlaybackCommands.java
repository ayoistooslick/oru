package com.oru.app.media;

import android.os.Bundle;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.SessionCommand;

/**
 * The two controls the stock media notification does not offer. They travel to
 * the session as custom commands, so the notification, the lock screen and the
 * app's own bars all change the same player.
 */
@OptIn(markerClass = UnstableApi.class)
public final class PlaybackCommands {

    public static final SessionCommand SHUFFLE =
            new SessionCommand("com.oru.app.action.SHUFFLE", Bundle.EMPTY);
    public static final SessionCommand REPEAT =
            new SessionCommand("com.oru.app.action.REPEAT", Bundle.EMPTY);

    /**
     * The sleep timer lives in the service, not the screen: playback carries on
     * after the app is closed, and so must the timer. {@code minutes} is positive
     * for a duration, 0 to cancel, and -1 for "the end of this song".
     */
    public static final SessionCommand SLEEP_TIMER =
            new SessionCommand("com.oru.app.action.SLEEP_TIMER", Bundle.EMPTY);
    public static final String SLEEP_MINUTES = "minutes";

    private PlaybackCommands() {
    }
}
