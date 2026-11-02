package com.oru.app.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.oru.app.R;
import com.oru.app.data.model.FeedItem;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.data.repository.CommunityRepository;
import com.oru.app.databinding.FragmentHomeBinding;
import com.oru.app.ui.components.FeedAdapter;

import java.util.List;

/**
 * Home feed: following feed + discover placeholder. Matches the web app's
 * listen/home tab.
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private FeedAdapter feedAdapter;
    private MusicRepository musicRepo;
    private CommunityRepository communityRepo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        feedAdapter = new FeedAdapter();
        binding.feedRecyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.feedRecyclerView.setAdapter(feedAdapter);

        binding.swipeRefresh.setColorSchemeResources(R.color.flame);

        binding.swipeRefresh.setOnRefreshListener(() -> loadFeed(true));

        // Wire the error view retry.
        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            loadFeed(true);
        });

        if (musicRepo == null) {
            musicRepo = new MusicRepository(
                    com.oru.app.util.LazySession.getApiService(),
                    com.oru.app.util.LazySession.getGson());
            communityRepo = new CommunityRepository(
                    com.oru.app.util.LazySession.getApiService(),
                    com.oru.app.util.LazySession.getGson());
        }

        loadFeed(false);
    }

    private void loadFeed(boolean refresh) {
        binding.swipeRefresh.setRefreshing(refresh);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.feedRecyclerView.setVisibility(View.GONE);
        binding.emptyView.getRoot().setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        musicRepo.getFeedFollowing(new Callback<List<FeedItem>>() {
            @Override
            public void onSuccess(List<FeedItem> items) {
                binding.swipeRefresh.setRefreshing(false);
                binding.progressBar.setVisibility(View.GONE);
                if (items.isEmpty()) {
                    binding.emptyView.getRoot().setVisibility(View.VISIBLE);
                } else {
                    feedAdapter.submitList(items);
                    binding.feedRecyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                binding.swipeRefresh.setRefreshing(false);
                binding.progressBar.setVisibility(View.GONE);
                binding.errorView.getRoot().setVisibility(View.VISIBLE);
                binding.errorView.errorMessage.setText(
                        describe(error));
            }
        });
    }

    private String describe(Throwable error) {
        if (error instanceof java.io.IOException) {
            return getString(R.string.error_network);
        }
        return getString(R.string.error_unknown);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
