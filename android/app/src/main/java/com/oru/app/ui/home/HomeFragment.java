package com.oru.app.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.DiscoverResult;
import com.oru.app.data.model.RankingEntry;
import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.databinding.FragmentHomeBinding;
import com.oru.app.ui.components.FeedAdapter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Home, laid out like the web's Listen page: this week's chart, songs from the
 * artists you follow, then everything new.
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private final FeedAdapter feedAdapter = new FeedAdapter();
    private MusicRepository musicRepo;

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

        binding.feedRecyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.feedRecyclerView.setAdapter(feedAdapter);

        binding.swipeRefresh.setColorSchemeResources(R.color.flame);
        binding.swipeRefresh.setOnRefreshListener(() -> loadHome(true));

        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            loadHome(true);
        });

        if (musicRepo == null) {
            musicRepo = new MusicRepository(
                    com.oru.app.util.LazySession.getApiService(),
                    com.oru.app.util.LazySession.getGson());
        }

        loadHome(false);
    }

    private void loadHome(boolean refresh) {
        binding.swipeRefresh.setRefreshing(refresh);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.feedRecyclerView.setVisibility(View.GONE);
        binding.emptyView.getRoot().setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.GONE);
        binding.homeGreeting.setText(greeting());
        binding.homeSubtitle.setText("Songs from who you follow, and what's new.");

        musicRepo.weeklyRankings(new Callback<List<RankingEntry>>() {
            @Override
            public void onSuccess(List<RankingEntry> rankings) {
                loadFollowing(rankings);
            }

            @Override
            public void onFailure(Throwable error) {
                loadFollowing(null);
            }
        });
    }

    private void loadFollowing(List<RankingEntry> rankings) {
        musicRepo.getFeedFollowing(new Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> songs) {
                loadNew(rankings, songs);
            }

            @Override
            public void onFailure(Throwable error) {
                loadNew(rankings, null);
            }
        });
    }

    private void loadNew(List<RankingEntry> rankings, List<Song> following) {
        musicRepo.getDiscover(new Callback<DiscoverResult>() {
            @Override
            public void onSuccess(DiscoverResult result) {
                render(rankings, following, result != null ? result.songs : null);
            }

            @Override
            public void onFailure(Throwable error) {
                if ((rankings == null || rankings.isEmpty())
                        && (following == null || following.isEmpty())) {
                    if (binding == null) {
                        return;
                    }
                    binding.progressBar.setVisibility(View.GONE);
                    binding.errorView.getRoot().setVisibility(View.VISIBLE);
                    binding.errorView.errorMessage.setText(
                            com.oru.app.ui.components.UiErrors
                                    .message(requireContext(), error));
                    return;
                }
                render(rankings, following, null);
            }
        });
    }

    private void render(List<RankingEntry> rankings, List<Song> following,
            List<Song> discover) {
        if (binding == null) {
            return;
        }
        binding.swipeRefresh.setRefreshing(false);
        binding.progressBar.setVisibility(View.GONE);

        List<RecyclerView.Adapter<? extends RecyclerView.ViewHolder>> parts =
                new ArrayList<>();
        if (rankings != null && !rankings.isEmpty()) {
            parts.add(new SectionAdapter("This week", "Most played"));
            parts.add(new RankAdapter(rankings));
        }
        if (following != null && !following.isEmpty()) {
            parts.add(new SectionAdapter("From artists you follow",
                    "New songs by the artists you follow."));
            FeedAdapter rows = new FeedAdapter();
            parts.add(rows);
            rows.submitList(following);
        }
        if (discover != null && !discover.isEmpty()) {
            parts.add(new SectionAdapter("New songs", "Everything published lately."));
            FeedAdapter rows = new FeedAdapter();
            parts.add(rows);
            rows.submitList(discover);
        }

        if (parts.isEmpty()) {
            binding.emptyView.getRoot().setVisibility(View.VISIBLE);
            return;
        }
        binding.feedRecyclerView.setAdapter(new ConcatAdapter(parts));
        binding.feedRecyclerView.setVisibility(View.VISIBLE);
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            return "Good morning";
        }
        if (hour < 18) {
            return "Good afternoon";
        }
        return "Good evening";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
