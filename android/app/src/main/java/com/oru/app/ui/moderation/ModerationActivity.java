package com.oru.app.ui.moderation;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.Report;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.ModerationRepository;
import com.oru.app.databinding.ActivityModerationBinding;
import com.oru.app.ui.moderation.ReportAdapter;
import android.view.View;
import com.oru.app.util.LazySession;

import java.util.List;

/**
 * Moderator reports list + decision screen. Requires the user to be an admin
 * or moderator (the backend enforces this).
 */
public class ModerationActivity extends AppCompatActivity {

    private ActivityModerationBinding binding;
    private ReportAdapter adapter;
    private ModerationRepository modRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityModerationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new ReportAdapter();
        adapter.setListener(this::decide);
        binding.recyclerView.setLayoutManager(
                new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            loadReports(true);
        });

        if (modRepo == null) {
            modRepo = new ModerationRepository(
                    LazySession.getApiService(),
                    LazySession.getGson());
        }

        loadReports(false);
    }

    /**
     * A decision needs both what should happen and the reason it was taken, so
     * the dialog asks for the action, then the reason.
     */
    private void decide(Report report) {
        String[] actions = {"hide", "restore", "dismiss"};
        String[] labels = {"Take it down", "Put it back", "Nothing wrong"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(report.targetType + " #" + report.targetId)
                .setItems(labels, (dialog, which) -> askReason(report, actions[which]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void askReason(Report report, String action) {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * density);

        android.widget.EditText reason = new android.widget.EditText(this);
        reason.setHint("Why (at least a few words)");
        reason.setMinLines(2);
        reason.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        reason.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setPadding(pad, pad, pad, 0);
        box.addView(reason);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Why")
                .setView(box)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Save decision", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(
                androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String text = reason.getText().toString().trim();
                    if (text.length() < 5) {
                        reason.setError("Write at least a few words");
                        return;
                    }
                    dialog.dismiss();
                    save(report, action, text);
                }));
        dialog.show();
    }

    private void save(Report report, String action, String reason) {
        binding.progressBar.setVisibility(View.VISIBLE);
        modRepo.moderationDecision(report.id, action, reason,
                new Callback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        binding.progressBar.setVisibility(View.GONE);
                        android.widget.Toast.makeText(ModerationActivity.this,
                                "Decision saved.", android.widget.Toast.LENGTH_LONG)
                                .show();
                        loadReports(false);
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        binding.progressBar.setVisibility(View.GONE);
                        android.widget.Toast.makeText(ModerationActivity.this,
                                com.oru.app.ui.components.UiErrors
                                        .message(ModerationActivity.this, error),
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void loadReports(boolean refresh) {
        binding.swipeRefresh.setRefreshing(refresh);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.emptyView.getRoot().setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        modRepo.listReports(new Callback<List<Report>>() {
            @Override
            public void onSuccess(List<Report> reports) {
                binding.swipeRefresh.setRefreshing(false);
                binding.progressBar.setVisibility(View.GONE);
                if (reports == null || reports.isEmpty()) {
                    binding.emptyView.getRoot().setVisibility(View.VISIBLE);
                } else {
                    adapter.submitList(reports);
                    binding.recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                binding.swipeRefresh.setRefreshing(false);
                binding.progressBar.setVisibility(View.GONE);
                binding.errorView.getRoot().setVisibility(View.VISIBLE);
                binding.errorView.errorMessage.setText(
                        com.oru.app.ui.components.UiErrors
                                .message(ModerationActivity.this, error));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
