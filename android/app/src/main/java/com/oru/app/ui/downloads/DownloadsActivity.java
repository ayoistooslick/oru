package com.oru.app.ui.downloads;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.oru.app.data.model.Song;
import com.oru.app.data.offline.Downloads;
import com.oru.app.databinding.ActivityDownloadsBinding;
import com.oru.app.ui.player.PlayerActivity;

/** What is saved on this device, and how to free the space again. */
public class DownloadsActivity extends AppCompatActivity {

    private ActivityDownloadsBinding binding;
    private DownloadAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDownloadsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new DownloadAdapter(new DownloadAdapter.Listener() {
            @Override
            public void onPlay(Song song) {
                Intent intent = new Intent(DownloadsActivity.this,
                        PlayerActivity.class);
                intent.putExtra("songId", song.id);
                startActivity(intent);
            }

            @Override
            public void onRemove(Song song) {
                askRemove(song);
            }
        });
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        if (binding == null) {
            return;
        }
        java.util.List<Song> saved = Downloads.songs(this);
        adapter.submit(saved);
        boolean empty = saved.isEmpty();
        binding.emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.summaryText.setText(empty ? ""
                : saved.size() + (saved.size() == 1 ? " song · " : " songs · ")
                        + sizeLabel(Downloads.totalBytes(this)));
    }

    private String sizeLabel(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.1f MB",
                    bytes / (1024.0 * 1024.0));
        }
        if (bytes >= 1024L) {
            return String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    private void askRemove(Song song) {
        new AlertDialog.Builder(this)
                .setTitle("Remove download?")
                .setMessage("\"" + song.title
                        + "\" will be deleted from this device.")
                .setNegativeButton("Keep", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    Downloads.remove(this, song.id);
                    load();
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
