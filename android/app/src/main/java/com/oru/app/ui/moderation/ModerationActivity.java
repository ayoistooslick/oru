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
