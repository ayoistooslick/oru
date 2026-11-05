package com.oru.app.ui.moderation;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.oru.app.data.model.Report;
import com.oru.app.databinding.ItemReportBinding;

public class ReportAdapter extends ListAdapter<Report, ReportAdapter.VH> {

    public interface Listener {
        void onDecide(Report report);
    }

    private Listener listener;

    public ReportAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Report> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Report>() {
                @Override
                public boolean areItemsTheSame(@NonNull Report oldItem,
                        @NonNull Report newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Report oldItem,
                        @NonNull Report newItem) {
                    return oldItem.status.equals(newItem.status);
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReportBinding b = ItemReportBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemReportBinding binding;

        VH(ItemReportBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Report report) {
            binding.targetText.setText(
                    report.targetType + " · #" + report.targetId);
            binding.reasonText.setText(report.reason);
            binding.detailsText.setText(report.details);
            binding.statusText.setText(report.status);
            binding.statusText.setTextColor(
                    binding.getRoot().getContext().getColor(
                            report.status.equals("resolved")
                                    ? com.oru.app.R.color.ink_faint
                                    : com.oru.app.R.color.flame));
            binding.decideButton.setText(
                    "open".equals(report.status) ? "Decide" : "Change decision");
            binding.decideButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDecide(report);
                }
            });
        }
    }
}
