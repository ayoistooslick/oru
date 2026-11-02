package com.oru.app.ui.shares;

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
import com.oru.app.data.model.SharedSong;
import com.oru.app.data.repository.Callback;
import com.oru.app.data.repository.SharingRepository;
import com.oru.app.databinding.FragmentSharesBinding;
import com.oru.app.ui.shares.SharesAdapter;
import android.view.View;

import java.util.List;

public class SharesFragment extends Fragment {

    private FragmentSharesBinding binding;
    private SharesAdapter adapter;
    private SharingRepository sharingRepo;

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
        binding.swipeRefresh.setRefreshing(refresh);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.emptyView.getRoot().setVisibility(View.GONE);
        binding.errorView.getRoot().setVisibility(View.GONE);

        sharingRepo.listSharedSongs(new Callback<List<SharedSong>>() {
            @Override
            public void onSuccess(List<SharedSong> shares) {
                binding.swipeRefresh.setRefreshing(false);
                binding.progressBar.setVisibility(View.GONE);
                if (shares == null || shares.isEmpty()) {
                    binding.emptyView.getRoot().setVisibility(View.VISIBLE);
                } else {
                    adapter.submitList(shares);
                    binding.recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                binding.swipeRefresh.setRefreshing(false);
                binding.progressBar.setVisibility(View.GONE);
                binding.errorView.getRoot().setVisibility(View.VISIBLE);
                binding.errorView.errorMessage.setText(
                        getString(R.string.error_network));
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
