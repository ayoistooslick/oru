package com.oru.app.media;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.CommandButton;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;

import com.google.common.collect.ImmutableList;
import com.oru.app.R;
import com.oru.app.util.NowPlaying;

/**
 * The media notification, dressed for ORU: song and artist named even when the
 * session's metadata arrives empty, and shuffle and repeat beside the usual
 * previous / play / next.
 */
@OptIn(markerClass = UnstableApi.class)
public final class OruNotificationProvider extends DefaultMediaNotificationProvider {

    /** Compact-view slot for each of the three buttons the collapsed bar shows. */
    private static final int SLOT_UNUSED = -1;
    private static final int SLOT_PREVIOUS = 0;
    private static final int SLOT_PLAY = 1;
    private static final int SLOT_NEXT = 2;

    public OruNotificationProvider(Context context) {
        super(context.getApplicationContext());
    }

    @Override
    @Nullable
    protected CharSequence getNotificationContentTitle(MediaMetadata metadata) {
        CharSequence title = super.getNotificationContentTitle(metadata);
        if (title != null && title.length() > 0) {
            return title;
        }
        return NowPlaying.title();
    }

    @Override
    @Nullable
    protected CharSequence getNotificationContentText(MediaMetadata metadata) {
        CharSequence artist = super.getNotificationContentText(metadata);
        if (artist != null && artist.length() > 0) {
            return artist;
        }
        return NowPlaying.artist();
    }

    @Override
    protected ImmutableList<CommandButton> getMediaButtons(MediaSession session,
            Player.Commands playerCommands, ImmutableList<CommandButton> customLayout,
            boolean showPauseButton) {
        ImmutableList.Builder<CommandButton> buttons = ImmutableList.builder();
        // Five actions fit; the middle three are the ones the bar collapses to.
        buttons.add(custom(PlaybackCommands.SHUFFLE, R.drawable.ic_shuffle,
                "Shuffle", SLOT_UNUSED));
        if (playerCommands.containsAny(Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
            buttons.add(player(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    R.drawable.ic_skip_back, "Previous song", SLOT_PREVIOUS));
        }
        if (playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
            buttons.add(player(Player.COMMAND_PLAY_PAUSE,
                    showPauseButton ? R.drawable.ic_pause : R.drawable.ic_play,
                    showPauseButton ? "Pause" : "Play", SLOT_PLAY));
        }
        if (playerCommands.containsAny(Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
            buttons.add(player(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    R.drawable.ic_skip_forward, "Next song", SLOT_NEXT));
        }
        buttons.add(custom(PlaybackCommands.REPEAT, R.drawable.ic_repeat,
                "Repeat", SLOT_UNUSED));
        return buttons.build();
    }

    private static CommandButton player(int command, int icon, String name,
            int slot) {
        return new CommandButton.Builder()
                .setPlayerCommand(command)
                .setIconResId(icon)
                .setDisplayName(name)
                .setExtras(slot(slot))
                .build();
    }

    private static CommandButton custom(androidx.media3.session.SessionCommand command,
            int icon, String name, int slot) {
        return new CommandButton.Builder()
                .setSessionCommand(command)
                .setIconResId(icon)
                .setDisplayName(name)
                .setExtras(slot(slot))
                .build();
    }

    private static Bundle slot(int index) {
        Bundle extras = new Bundle();
        extras.putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, index);
        return extras;
    }
}
