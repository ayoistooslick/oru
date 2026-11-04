package com.oru.app.ui.studio;

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
import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.MusicRepository;
import com.oru.app.databinding.FragmentStudioBinding;
import com.oru.app.ui.studio.StudioAdapter;
import android.view.View;

import java.util.List;

/**
 * Studio tab: my unreleased/pending songs for an artist.
 * Mirrors the web app studio page.
 */
public class StudioFragment extends Fragment {

    private FragmentStudioBinding binding;
    private StudioAdapter adapter;
    private MusicRepository musicRepo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentStudioBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new StudioAdapter();
        binding.recyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            loadSongs(true);
        });

        if (musicRepo == null) {
            musicRepo = new MusicRepository(
                    com.oru.app.util.LazySession.getApiService(),
                    com.oru.app.util.LazySession.getGson());
        }

        loadSongs(false);
    }

    private void loadSongs(boolean refresh) {
        binding.swipeRefresh.setRefreshing(refresh);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.emptyView.getRoot().setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        musicRepo.getMySongs(new Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> songs) {
                binding.swipeRefresh.setRefreshing(false);
                binding.progressBar.setVisibility(View.GONE);
                if (songs == null || songs.isEmpty()) {
                    binding.emptyView.getRoot().setVisibility(View.VISIBLE);
                } else {
                    adapter.submitList(songs);
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
