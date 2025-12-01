package com.pasich.mynotes.utils.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.HelpSection;
import com.pasich.mynotes.databinding.ItemHelpFeatureBinding;
import com.pasich.mynotes.databinding.ItemHelpHeaderBinding;
import com.pasich.mynotes.databinding.ItemHelpSectionTitleBinding;

import java.util.List;

public class HelpSectionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<HelpSection> sections;

    public HelpSectionAdapter(List<HelpSection> sections) {
        this.sections = sections;
    }

    @Override
    public int getItemViewType(int position) {
        return sections.get(position).type();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        return switch (viewType) {
            case HelpSection.TYPE_HEADER -> new HeaderViewHolder(
                    ItemHelpHeaderBinding.inflate(inflater, parent, false)
            );
            case HelpSection.TYPE_SECTION_TITLE -> new SectionTitleViewHolder(
                    ItemHelpSectionTitleBinding.inflate(inflater, parent, false)
            );
            default -> new FeatureViewHolder(
                    ItemHelpFeatureBinding.inflate(inflater, parent, false)
            );
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        HelpSection section = sections.get(position);

        switch (holder.getItemViewType()) {
            case HelpSection.TYPE_HEADER:
                ((HeaderViewHolder) holder).bind(section);
                break;
            case HelpSection.TYPE_SECTION_TITLE:
                ((SectionTitleViewHolder) holder).bind(section);
                break;
            case HelpSection.TYPE_FEATURE:
                ((FeatureViewHolder) holder).bind(section);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return sections.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final ItemHelpHeaderBinding binding;

        HeaderViewHolder(ItemHelpHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(HelpSection section) {
            binding.versionActual.setText(itemView.getContext().getString(
                    R.string.actual_version,
                    section.additionalInfo()
            ));
            if (section.description() != null) {
                binding.subtitleText.setText(section.description());
                binding.subtitleText.setVisibility(View.VISIBLE);
            } else {
                binding.subtitleText.setVisibility(View.GONE);
            }
        }
    }

    static class SectionTitleViewHolder extends RecyclerView.ViewHolder {
        private final ItemHelpSectionTitleBinding binding;

        SectionTitleViewHolder(ItemHelpSectionTitleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(HelpSection section) {
            binding.sectionTitle.setText(section.title());
            if (section.iconRes() != null) {
                binding.sectionIcon.setImageResource(section.iconRes());
                binding.sectionIcon.setVisibility(View.VISIBLE);
            } else {
                binding.sectionIcon.setVisibility(View.GONE);
            }
        }
    }

    static class FeatureViewHolder extends RecyclerView.ViewHolder {
        private final ItemHelpFeatureBinding binding;

        FeatureViewHolder(ItemHelpFeatureBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(HelpSection section) {
            binding.featureTitle.setText(section.title());
            binding.featureDescription.setText(section.description());

            if (section.iconRes() != null) {
                binding.featureIcon.setImageResource(section.iconRes());
                binding.featureIcon.setVisibility(View.VISIBLE);
            } else {
                binding.featureIcon.setVisibility(View.GONE);
            }
        }
    }
}
