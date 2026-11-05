package com.oru.app.ui.shares;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.data.model.ExternalShare;
import com.oru.app.databinding.ItemExternalShareBinding;

import java.util.Objects;

/** Songs somebody shared from outside ORU, with a withdraw action. */
public class ExternalShareAdapter
        extends ListAdapter<ExternalShare, ExternalShareAdapter.VH> {

    public interface Listener {
        void onWithdraw(ExternalShare share);
    }

    private Listener listener;

    public ExternalShareAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<ExternalShare> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ExternalShare>() {
                @Override
                public boolean areItemsTheSame(@NonNull ExternalShare oldItem,
                        @NonNull ExternalShare newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull ExternalShare oldItem,
                        @NonNull ExternalShare newItem) {
                    return Objects.equals(oldItem.title, newItem.title)
                            && Objects.equals(oldItem.url, newItem.url);
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemExternalShareBinding b = ItemExternalShareBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    class VH extends RecyclerView.ViewHolder {
        private final ItemExternalShareBinding binding;

        VH(ItemExternalShareBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ExternalShare share) {
            binding.titleText.setText(share.title);
            String artist = share.artistName != null ? share.artistName : "";
            if (share.sharer != null && share.sharer.handle != null) {
                artist = artist + " · shared by " + share.sharer.handle;
            }
            binding.artistText.setText(artist);
            binding.urlText.setText(share.url);
            android.text.util.Linkify.addLinks(binding.urlText,
                    android.text.util.Linkify.WEB_URLS);
            binding.urlText.setMovementMethod(
                    android.text.method.LinkMovementMethod.getInstance());
            boolean hasNote = share.note != null && !share.note.trim().isEmpty();
            binding.noteText.setVisibility(hasNote ? android.view.View.VISIBLE
                    : android.view.View.GONE);
            binding.noteText.setText(hasNote ? share.note : "");
            binding.withdrawButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onWithdraw(share);
                }
            });
        }
    }
}
