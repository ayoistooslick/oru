package com.oru.app.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.R;
import com.oru.app.data.model.FeedItem;
import com.oru.app.data.model.Playlist;
import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.CommunityRepository;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.databinding.FragmentLibraryBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Library tab: playlists + liked songs + my songs + releases.
 * Mirrors the web app library page.
 */
public class LibraryFragment extends Fragment {

    private FragmentLibraryBinding binding;
    private LibraryAdapter adapter;
    private MusicRepository musicRepo;
    private CommunityRepository communityRepo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new LibraryAdapter();
        binding.recyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            loadLibrary(true);
        });

        if (musicRepo == null) {
            musicRepo = new MusicRepository(
                    com.oru.app.util.LazySession.getApiService(),
                    com.oru.app.util.LazySession.getGson());
            communityRepo = new CommunityRepository(
                    com.oru.app.util.LazySession.getApiService(),
                    com.oru.app.util.LazySession.getGson());
        }

        loadLibrary(false);
    }

    private void loadLibrary(boolean refresh) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.emptyView.getRoot().setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        List<LibraryItem> items = new ArrayList<>();

        communityRepo.getMyLikes(new Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> likes) {
                if (likes != null && !likes.isEmpty()) {
                    items.add(LibraryItem.songs(likes, LibraryItem.Type.LIKES));
                }
                loadPlaylists(items);
            }

            @Override
            public void onFailure(Throwable error) {
                loadPlaylists(items);
            }
        });
    }

    private void loadPlaylists(List<LibraryItem> items) {
        communityRepo.listPlaylists(new Callback<List<Playlist>>() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                if (playlists != null && !playlists.isEmpty()) {
                    items.add(LibraryItem.playlists(playlists, LibraryItem.Type.PLAYLISTS));
                }
                finishLoad(items);
            }

            @Override
            public void onFailure(Throwable error) {
                finishLoad(items);
            }
        });
    }

    private void finishLoad(List<LibraryItem> items) {
        binding.progressBar.setVisibility(View.GONE);
        if (items.isEmpty()) {
            binding.emptyView.getRoot().setVisibility(View.VISIBLE);
        } else {
            adapter.setItems(items);
            binding.recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
