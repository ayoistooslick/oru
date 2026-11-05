package com.oru.app.ui.shares;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.oru.app.R;
import com.oru.app.data.model.ExternalShare;
import com.oru.app.data.model.Song;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.SharingRepository;
import com.oru.app.databinding.FragmentSharesBinding;
import com.oru.app.ui.components.UiErrors;

import java.util.List;

/**
 * Shares tab: the songs I sent to artists, the links I shared from outside ORU,
 * and the requests waiting on my decision as an artist.
 */
public class SharesFragment extends Fragment {

    private FragmentSharesBinding binding;
    private SharesAdapter adapter;
    private ShareRequestAdapter requestAdapter;
    private ExternalShareAdapter externalAdapter;
    private SharingRepository sharingRepo;

    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> shareLauncher =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts
                            .StartActivityForResult(),
                    result -> loadShares(false));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSharesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new SharesAdapter();
        binding.recyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        requestAdapter = new ShareRequestAdapter();
        requestAdapter.setListener(new ShareRequestAdapter.Listener() {
            @Override
            public void onApprove(Song song) {
                decide(song, "approve");
            }

            @Override
            public void onReject(Song song) {
                decide(song, "reject");
            }
        });
        binding.requestsRecyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.requestsRecyclerView.setAdapter(requestAdapter);

        externalAdapter = new ExternalShareAdapter();
        externalAdapter.setListener(share -> withdrawLink(share));
        binding.externalRecyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        binding.externalRecyclerView.setAdapter(externalAdapter);

        binding.shareSongButton.setOnClickListener(v -> shareLauncher.launch(
                new android.content.Intent(requireContext(), ShareActivity.class)));

        binding.errorView.retryButton.setOnClickListener(v -> {
            binding.errorView.getRoot().setVisibility(View.GONE);
            loadShares(true);
        });

        if (sharingRepo == null) {
            sharingRepo = new SharingRepository(
                    com.oru.app.util.LazySession.getApiService(),
                    com.oru.app.util.LazySession.getGson());
        }

        loadShares(false);
    }

    private void loadShares(boolean refresh) {
        if (binding == null) {
            return;
        }
        binding.swipeRefresh.setRefreshing(refresh);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.errorView.getRoot().setVisibility(View.GONE);
        loadSharedSongs();
        loadRequests();
        loadExternalLinks();
    }

    private void loadSharedSongs() {
        sharingRepo.listSharedSongs(new Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> shares) {
                if (binding == null) {
                    return;
                }
                binding.swipeRefresh.setRefreshing(false);
                binding.progressBar.setVisibility(View.GONE);
                boolean empty = shares == null || shares.isEmpty();
                if (!empty) {
                    adapter.submitList(shares);
                }
                binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
                binding.songsEmptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(Throwable error) {
                if (binding == null) {
                    return;
                }
                binding.swipeRefresh.setRefreshing(false);
                binding.progressBar.setVisibility(View.GONE);
                binding.errorView.getRoot().setVisibility(View.VISIBLE);
                binding.errorView.errorMessage.setText(
                        UiErrors.message(requireContext(), error));
            }
        });
    }

    /** Only artists and moderators see requests; a refusal just hides them. */
    private void loadRequests() {
        sharingRepo.listPendingShares(new Callback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> requests) {
                if (binding == null) {
                    return;
                }
                boolean empty = requests == null || requests.isEmpty();
                if (!empty) {
                    requestAdapter.submitList(requests);
                }
                binding.requestsRecyclerView.setVisibility(
                        empty ? View.GONE : View.VISIBLE);
                binding.requestsHeader.setVisibility(
                        empty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onFailure(Throwable error) {
                if (binding == null) {
                    return;
                }
                binding.requestsRecyclerView.setVisibility(View.GONE);
                binding.requestsHeader.setVisibility(View.GONE);
            }
        });
    }

    private void loadExternalLinks() {
        sharingRepo.listExternalShares(new Callback<List<ExternalShare>>() {
            @Override
            public void onSuccess(List<ExternalShare> shares) {
                if (binding == null) {
                    return;
                }
                boolean empty = shares == null || shares.isEmpty();
                if (!empty) {
                    externalAdapter.submitList(shares);
                }
                binding.externalRecyclerView.setVisibility(
                        empty ? View.GONE : View.VISIBLE);
                binding.externalEmptyText.setVisibility(
                        empty ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(Throwable error) {
                if (binding == null) {
                    return;
                }
                binding.externalEmptyText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void decide(Song song, String action) {
        binding.progressBar.setVisibility(View.VISIBLE);
        sharingRepo.shareDecision(song.id, action, new Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                binding.progressBar.setVisibility(View.GONE);
                toastMessage("approve".equals(action)
                        ? song.title + " is published."
                        : song.title + " was rejected.");
                loadShares(false);
            }

            @Override
            public void onFailure(Throwable error) {
                binding.progressBar.setVisibility(View.GONE);
                toastMessage(UiErrors.message(requireContext(), error));
            }
        });
    }

    private void withdrawLink(ExternalShare share) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Withdraw this link?")
                .setMessage(share.title)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Withdraw", (dialog, which) -> {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    sharingRepo.revokeExternalShare(share.id,
                            new Callback<Void>() {
                                @Override
                                public void onSuccess(Void ignored) {
                                    binding.progressBar.setVisibility(View.GONE);
                                    loadShares(false);
                                }

                                @Override
                                public void onFailure(Throwable error) {
                                    binding.progressBar.setVisibility(View.GONE);
                                    toastMessage(UiErrors.message(
                                            requireContext(), error));
                                }
                            });
                })
                .show();
    }

    private void toastMessage(String message) {
        if (binding == null) {
            return;
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
