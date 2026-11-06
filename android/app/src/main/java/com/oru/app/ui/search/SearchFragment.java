package com.oru.app.ui.search;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;

import com.google.android.material.chip.Chip;
import com.oru.app.R;
import com.oru.app.data.model.Artist;
import com.oru.app.data.model.Genre;
import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.databinding.FragmentSearchBinding;

import java.util.ArrayList;
import java.util.List;

/** Native search for published songs and artist profiles. */
public class SearchFragment extends Fragment {

    private static final long SEARCH_DELAY_MS = 280L;
    private static final String PREFS = "oru";
    private static final String RECENT_KEY = "recent-searches";
    private static final int RECENT_LIMIT = 5;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pendingSearch = this::performSearch;
    private FragmentSearchBinding binding;
    private SearchAdapter adapter;
    private MusicRepository musicRepo;
    private int requestGeneration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view,
            @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new SearchAdapter();
        binding.searchRecyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.searchRecyclerView.setAdapter(adapter);
        // Playlists are not searchable in the public API. Do not expose a
        // filter that can only return no results.
        binding.chipPlaylists.setVisibility(View.GONE);
        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            onSearchChanged();
        });
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start,
                    int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start,
                    int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                refreshChips();
                onSearchChanged();
            }
        });
        binding.searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                handler.removeCallbacks(pendingSearch);
                saveRecent(binding.searchEditText.getText().toString());
                performSearch();
                return true;
            }
            return false;
        });
        binding.filterChipGroup.setOnCheckedStateChangeListener(
                (group, checkedIds) -> onSearchChanged());
        musicRepo = new MusicRepository(
                com.oru.app.util.LazySession.getApiService(),
                com.oru.app.util.LazySession.getGson());
        loadGenres();
        refreshChips();
        loadDiscover();
    }

    /** The genre shortcuts the web shows above an empty search box. */
    private void loadGenres() {
        musicRepo.listGenres(new Callback<List<Genre>>() {
            @Override
            public void onSuccess(List<Genre> genres) {
                if (binding == null || genres == null) return;
                binding.genreChipGroup.removeAllViews();
                for (Genre genre : genres) {
                    if (genre.name == null || genre.name.trim().isEmpty()) continue;
                    Chip chip = chip(genre.name);
                    chip.setOnClickListener(v -> setQuery(genre.name));
                    binding.genreChipGroup.addView(chip);
                }
                refreshChips();
            }

            @Override
            public void onFailure(Throwable error) {
                // The box below still searches; no need to shout about chips.
            }
        });
    }

    private Chip chip(String label) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setCheckable(false);
        chip.setClickable(true);
        chip.setChipBackgroundColor(ColorStateList.valueOf(
                requireContext().getColor(R.color.card_hi)));
        chip.setTextColor(requireContext().getColor(R.color.ink_soft));
        chip.setChipStrokeWidth(0f);
        return chip;
    }

    private void setQuery(String term) {
        if (binding == null) return;
        binding.searchEditText.setText(term);
        binding.searchEditText.setSelection(term.length());
    }

    /**
     * Recent terms, saved the way the web saves them: five at most, newest
     * first, case-insensitive duplicates collapsed.
     */
    private List<String> recentTerms() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(RECENT_KEY, "");
        List<String> terms = new ArrayList<>();
        if (stored != null && !stored.isEmpty()) {
            for (String term : stored.split("\n")) {
                if (!term.trim().isEmpty()) terms.add(term);
            }
        }
        return terms;
    }

    private void saveRecent(String term) {
        String clean = term.trim();
        if (clean.isEmpty()) return;
        List<String> next = new ArrayList<>();
        next.add(clean);
        for (String old : recentTerms()) {
            if (!old.equalsIgnoreCase(clean) && next.size() < RECENT_LIMIT) {
                next.add(old);
            }
        }
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(RECENT_KEY,
                        android.text.TextUtils.join("\n", next.toArray(new String[0])))
                .apply();
        refreshChips();
    }

    /** Genres and recents belong to an empty box; scope chips to a query. */
    private void refreshChips() {
        if (binding == null) return;
        boolean empty = binding.searchEditText.getText().toString().trim().isEmpty();
        binding.genreScroll.setVisibility(empty
                && binding.genreChipGroup.getChildCount() > 0
                        ? View.VISIBLE : View.GONE);
        binding.filterChipGroup.setVisibility(empty ? View.GONE : View.VISIBLE);

        binding.recentChipGroup.removeAllViews();
        List<String> recent = recentTerms();
        boolean showRecent = empty && !recent.isEmpty();
        for (String term : recent) {
            Chip chip = chip(term);
            chip.setOnClickListener(v -> setQuery(term));
            binding.recentChipGroup.addView(chip);
        }
        binding.recentGroup.setVisibility(showRecent ? View.VISIBLE : View.GONE);
    }

    private void onSearchChanged() {
        handler.removeCallbacks(pendingSearch);
        requestGeneration++;
        if (binding == null) return;
        if (binding.searchEditText.getText().toString().trim().isEmpty()) {
            loadDiscover();
        } else {
            handler.postDelayed(pendingSearch, SEARCH_DELAY_MS);
        }
    }

    private void performSearch() {
        if (binding == null) return;
        final String query = binding.searchEditText.getText().toString().trim();
        if (query.isEmpty()) {
            loadDiscover();
            return;
        }
        final int generation = ++requestGeneration;
        final boolean songsOnly = binding.filterChipGroup.getCheckedChipId()
                == R.id.chipSongs;
        final boolean artistsOnly = binding.filterChipGroup.getCheckedChipId()
                == R.id.chipArtists;
        final List<SearchResult> results = new ArrayList<>();
        final int[] pending = { (artistsOnly ? 0 : 1) + (songsOnly ? 0 : 1) };
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.searchRecyclerView.setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        if (!artistsOnly) {
            musicRepo.listSongs(query, 50, 0, new Callback<List<Song>>() {
                @Override public void onSuccess(List<Song> songs) {
                    if (!isCurrent(generation)) return;
                    if (songs != null) for (Song song : songs) {
                        results.add(new SearchResult(song, SearchResult.Type.SONG));
                    }
                    completeSearch(generation, query, results, pending);
                }
                @Override public void onFailure(Throwable error) {
                    showSearchError(generation, error);
                }
            });
        }
        if (!songsOnly) {
            musicRepo.searchArtists(query, new Callback<List<Artist>>() {
                @Override public void onSuccess(List<Artist> artists) {
                    if (!isCurrent(generation)) return;
                    if (artists != null) for (Artist artist : artists) {
                        results.add(new SearchResult(artist, SearchResult.Type.ARTIST));
                    }
                    completeSearch(generation, query, results, pending);
                }
                @Override public void onFailure(Throwable error) {
                    showSearchError(generation, error);
                }
            });
        }
    }

    private void completeSearch(int generation, String query,
            List<SearchResult> results, int[] pending) {
        if (!isCurrent(generation)) return;
        pending[0]--;
        if (pending[0] > 0) return;
        binding.progressBar.setVisibility(View.GONE);
        if (results.isEmpty()) {
            binding.errorView.getRoot().setVisibility(View.VISIBLE);
            binding.errorView.errorMessage.setText("No results for \"" + query + "\"");
        } else {
            adapter.submitList(new ArrayList<>(results));
            binding.searchRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private boolean isCurrent(int generation) {
        return binding != null && generation == requestGeneration;
    }

    private void showSearchError(int generation, Throwable error) {
        if (!isCurrent(generation)) return;
        binding.progressBar.setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.VISIBLE);
        binding.errorView.errorMessage.setText(
                com.oru.app.ui.components.UiErrors.message(requireContext(), error));
    }

    private void loadDiscover() {
        if (binding == null) return;
        final int generation = ++requestGeneration;
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.searchRecyclerView.setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.GONE);
        musicRepo.getDiscover(new Callback<com.oru.app.data.model.DiscoverResult>() {
            @Override public void onSuccess(
                    com.oru.app.data.model.DiscoverResult result) {
                if (!isCurrent(generation)) return;
                binding.progressBar.setVisibility(View.GONE);
                List<SearchResult> results = new ArrayList<>();
                if (result != null && result.songs != null) {
                    for (Song song : result.songs) {
                        results.add(new SearchResult(song, SearchResult.Type.SONG));
                    }
                }
                adapter.submitList(results);
                binding.searchRecyclerView.setVisibility(View.VISIBLE);
            }
            @Override public void onFailure(Throwable error) {
                showSearchError(generation, error);
            }
        });
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(pendingSearch);
        requestGeneration++;
        binding = null;
        super.onDestroyView();
    }
}
