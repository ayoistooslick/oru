package com.oru.app.ui.notifications;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.data.model.DropNotification;
import com.oru.app.databinding.ItemNotificationBinding;
import com.oru.app.ui.components.Covers;
import com.oru.app.util.RelativeTime;

import java.util.ArrayList;
import java.util.List;

/**
 * The drops list: who dropped what, when, and a dot until it has been opened.
 */
public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.Holder> {

    public interface OnOpen {
        void open(DropNotification note);
    }

    private final List<DropNotification> items = new ArrayList<>();
    private final OnOpen onOpen;

    public NotificationsAdapter(OnOpen onOpen) {
        this.onOpen = onOpen;
    }

    public void submit(List<DropNotification> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    /** Everything is read now; the rows stay, the dots go. */
    public void markAllRead() {
        for (DropNotification note : items) {
            note.readAt = "read";
        }
        notifyDataSetChanged();
    }

    public int unreadCount() {
        int unread = 0;
        for (DropNotification note : items) {
            if (note.isUnread()) {
                unread++;
            }
        }
        return unread;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNotificationBinding binding = ItemNotificationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        DropNotification note = items.get(position);
        holder.binding.unreadDot.setVisibility(note.isUnread() ? View.VISIBLE : View.INVISIBLE);
        holder.binding.titleText.setText(note.title());
        String meta = note.kindLabel() + " · " + note.artistName();
        if (note.href == null) {
            // The song or release has come down since; say so instead of
            // promising something that will not play.
            meta = meta + " · no longer available";
        }
        holder.binding.metaText.setText(meta);
        holder.binding.timeText.setText(RelativeTime.of(note.createdAt));
        if (note.song != null) {
            Covers.loadSongCover(holder.binding.artworkImage, note.song.id, note.coverUrl());
        } else {
            Covers.load(holder.binding.artworkImage, note.coverUrl());
        }
        holder.binding.getRoot().setOnClickListener(v -> onOpen.open(note));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemNotificationBinding binding;

        Holder(ItemNotificationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
