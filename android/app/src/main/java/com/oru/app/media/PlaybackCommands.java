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

    private PlaybackCommands() {
    }
}
