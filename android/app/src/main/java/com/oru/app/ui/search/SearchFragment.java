package com.oru.app.ui.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.oru.app.R;
import com.oru.app.data.model.Artist;
import com.oru.app.data.model.Playlist;
import com.oru.app.data.model.Song;
import com.oru.app.data.model.DiscoverResult;
import com.oru.app.data.model.Artist;
import com.oru.app.data.model.Playlist;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.databinding.FragmentSearchBinding;

import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment {

    private FragmentSearchBinding binding;
    private SearchAdapter adapter;
    private MusicRepository musicRepo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new SearchAdapter();
        binding.searchRecyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.searchRecyclerView.setAdapter(adapter);

        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            onSearchChanged();
        });

        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start,
                    int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start,
                    int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                onSearchChanged();
            }
        });
        binding.searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        binding.filterChipGroup.setOnCheckedStateChangeListener(
                (group, checkedIds) -> onSearchChanged());

        if (musicRepo == null) {
            musicRepo = new MusicRepository(
                    com.oru.app.util.LazySession.getApiService(),
                    com.oru.app.util.LazySession.getGson());
        }

        // Initial state: show discover or recent.
        loadDiscover();
    }

    private void onSearchChanged() {
        // Debounce would be nicer; for now trigger on next layout pass.
        // Kept lightweight to avoid jank.
    }

    private void performSearch() {
        // Search maps to the songs list with client-side filter by title in
        // this MVP; a real search endpoint can be wired here.
        String query = binding.searchEditText.getText().toString().trim();
        if (query.isEmpty()) {
            loadDiscover();
            return;
        }
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.searchRecyclerView.setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        // The API searches titles, artists and stage names itself.
        musicRepo.listSongs(query, 50, 0, new Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> songs) {
                binding.progressBar.setVisibility(View.GONE);
                List<SearchResult> results = new ArrayList<>();
                if (songs != null) {
                    for (Song s : songs) {
                        results.add(new SearchResult(s, SearchResult.Type.SONG));
                    }
                }
                if (results.isEmpty()) {
                    binding.errorView.getRoot().setVisibility(View.VISIBLE);
                    binding.errorView.errorMessage.setText(
                            "No results for \"" + query + "\"");
                } else {
                    adapter.submitList(results);
                    binding.searchRecyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                binding.progressBar.setVisibility(View.GONE);
                binding.errorView.getRoot().setVisibility(View.VISIBLE);
                binding.errorView.errorMessage.setText(
                        com.oru.app.ui.components.UiErrors
                                .message(requireContext(), error));
            }
        });
    }

    private void loadDiscover() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.searchRecyclerView.setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        musicRepo.getDiscover(new Callback<com.oru.app.data.model.DiscoverResult>() {
            @Override
            public void onSuccess(
                    com.oru.app.data.model.DiscoverResult result) {
                binding.progressBar.setVisibility(View.GONE);
                List<SearchResult> results = new ArrayList<>();
                if (result != null && result.songs != null) {
                    for (Song s : result.songs) {
                        results.add(
                                new SearchResult(s, SearchResult.Type.SONG));
                    }
                }
                adapter.submitList(results);
                binding.searchRecyclerView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(Throwable error) {
                binding.progressBar.setVisibility(View.GONE);
                binding.errorView.getRoot().setVisibility(View.VISIBLE);
                binding.errorView.errorMessage.setText(
                        com.oru.app.ui.components.UiErrors
                                .message(requireContext(), error));
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
