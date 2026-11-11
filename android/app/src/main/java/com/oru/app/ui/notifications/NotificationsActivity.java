package com.oru.app.ui.notifications;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.oru.app.R;
import com.oru.app.data.model.DropNotification;
import com.oru.app.data.model.NotificationsPage;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.NotificationsRepository;
import com.oru.app.data.session.SessionManager;
import com.oru.app.databinding.ActivityNotificationsBinding;
import com.oru.app.ui.OpenLinkActivity;
import com.oru.app.ui.auth.AuthActivity;
import com.oru.app.util.LazySession;

/**
 * The bell: new music from the artists this listener follows.
 *
 * Following an artist is the whole opt-in, so this screen is where a follow
 * pays off. Rows open what they name through the same link handler the web
 * uses — oru://song/12 — so a drop and a shared link behave identically.
 */
public class NotificationsActivity extends AppCompatActivity {

    private ActivityNotificationsBinding binding;
    private NotificationsAdapter adapter;
    private NotificationsRepository repository;
    /** onCreate already loaded; the first onResume should not ask twice. */
    private boolean firstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!new SessionManager(this).isSignedIn()) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new NotificationsRepository(LazySession.getApiService(),
                LazySession.getGson());
        adapter = new NotificationsAdapter(this::open);
        binding.notificationsRecyclerView.setLayoutManager(
                new LinearLayoutManager(this));
        binding.notificationsRecyclerView.setAdapter(adapter);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_mark_all_read) {
                markAllRead();
                return true;
            }
            return false;
        });

        load();
    }

    private void load() {
        binding.progressBar.setVisibility(View.VISIBLE);
        repository.list(new Callback<NotificationsPage>() {
            @Override
            public void onSuccess(NotificationsPage page) {
                runOnUiThread(() -> {
                    if (binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    adapter.submit(page != null ? page.items : null);
                    boolean empty = page == null || page.items == null || page.items.isEmpty();
                    binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                    showMarkAll(adapter.unreadCount() > 0);
                });
            }

            @Override
            public void onFailure(Throwable error) {
                runOnUiThread(() -> {
                    if (binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    binding.emptyView.setVisibility(View.VISIBLE);
                    showMarkAll(false);
                    Toast.makeText(NotificationsActivity.this, R.string.error_network,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showMarkAll(boolean visible) {
        android.view.MenuItem item = binding.toolbar.getMenu()
                .findItem(R.id.action_mark_all_read);
        if (item != null) {
            item.setVisible(visible);
        }
    }

    private void markAllRead() {
        repository.markAllRead(new Callback<NotificationsPage.Marked>() {
            @Override
            public void onSuccess(NotificationsPage.Marked marked) {
                runOnUiThread(() -> {
                    if (binding == null) return;
                    adapter.markAllRead();
                    showMarkAll(false);
                });
            }

            @Override
            public void onFailure(Throwable error) {
                runOnUiThread(() -> Toast.makeText(NotificationsActivity.this,
                        R.string.error_network, Toast.LENGTH_SHORT).show());
            }
        });
    }

    /** Opening a row marks it read, then hands the link to the link handler. */
    private void open(DropNotification note) {
        if (note.isUnread()) {
            repository.markRead(note.id, new Callback<NotificationsPage.Read>() {
                @Override
                public void onSuccess(NotificationsPage.Read read) {
                    runOnUiThread(() -> {
                        note.readAt = "read";
                        adapter.notifyDataSetChanged();
                        showMarkAll(adapter.unreadCount() > 0);
                    });
                }

                @Override
                public void onFailure(Throwable error) {
                    // Opening matters more than the receipt.
                }
            });
        }

        String link = null;
        if (note.song != null && note.song.available) {
            link = "oru://song/" + note.song.id;
        } else if (note.release != null && note.release.available) {
            link = "oru://release/" + note.release.id;
        }
        if (link == null) {
            Toast.makeText(this, R.string.notification_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, OpenLinkActivity.class);
        intent.setData(Uri.parse(link));
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from a song may have changed what is unread.
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (binding != null) {
            load();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
