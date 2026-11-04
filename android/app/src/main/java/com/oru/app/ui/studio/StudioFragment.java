package com.oru.app.ui.studio;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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
import com.oru.app.ui.components.UiErrors;

import java.util.List;

/**
 * Studio tab: my unreleased/pending songs for an artist.
 * Mirrors the web app studio page.
 */
public class StudioFragment extends Fragment {

    private FragmentStudioBinding binding;
    private StudioAdapter adapter;
    private MusicRepository musicRepo;

    private final androidx.activity.result.ActivityResultLauncher<Intent> uploadLauncher =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts
                            .StartActivityForResult(),
                    result -> loadSongs(false));

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
        adapter.setListener(new StudioAdapter.Listener() {
            @Override
            public void onPublish(Song song) {
                act(song, true);
            }

            @Override
            public void onUnpublish(Song song) {
                act(song, false);
            }

            @Override
            public void onDelete(Song song) {
                binding.progressBar.setVisibility(View.VISIBLE);
                musicRepo.deleteSong(song.id, new Callback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        loadSongs(false);
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(requireContext(),
                                UiErrors.message(requireContext(), error),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        binding.recyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        binding.uploadButton.setOnClickListener(v -> uploadLauncher.launch(
                new Intent(requireContext(), UploadActivity.class)));

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

    /** Publishes or pulls a track back to draft. */
    private void act(Song song, boolean publish) {
        binding.progressBar.setVisibility(View.VISIBLE);
        Callback<Song> done = new Callback<Song>() {
            @Override
            public void onSuccess(Song updated) {
                loadSongs(false);
            }

            @Override
            public void onFailure(Throwable error) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(),
                        UiErrors.message(requireContext(), error),
                        Toast.LENGTH_LONG).show();
            }
        };
        if (publish) {
            musicRepo.publishSong(song.id, done);
        } else {
            musicRepo.unpublishSong(song.id, done);
        }
    }

    private void loadSongs(boolean refresh) {
        if (binding == null) {
            return;
        }
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
