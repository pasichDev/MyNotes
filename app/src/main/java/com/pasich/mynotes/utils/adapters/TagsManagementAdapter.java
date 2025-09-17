package com.pasich.mynotes.utils.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.ItemTagManagementBinding;
import com.pasich.mynotes.utils.managers.SystemTagsManager;

import java.util.ArrayList;
import java.util.List;

public class TagsManagementAdapter extends ListAdapter<Tag, TagsManagementAdapter.TagViewHolder> {

    public interface OnTagClickListener {
        void onTagClick(Tag tag, int position);
        void onTagLongClick(Tag tag, View anchorView);
        void onOptionsClick(Tag tag, View anchorView);
    }

    public interface OnTagMoveListener {
        void onDragCompleted(List<Tag> currentTagOrder);
    }

    private OnTagClickListener clickListener;
    private OnTagMoveListener moveListener;

    public TagsManagementAdapter() {
        super(new TagDiffCallback());
    }

    public void setOnTagClickListener(OnTagClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnTagMoveListener(OnTagMoveListener listener) {
        this.moveListener = listener;
    }

    // Method for UI-only movement (used during drag)
    public void moveItemUI(int fromPosition, int toPosition) {
        if (fromPosition == 0 || toPosition == 0) return;

        List<Tag> newList = new ArrayList<>(getCurrentList());
        Tag moved = newList.remove(fromPosition);
        newList.add(toPosition, moved);

        submitList(newList);
    }


    // Method called when drag operation is complete - save to database
    public void saveDragChangesToDatabase() {
        if (moveListener != null) {
            List<Tag> currentTags = getCurrentList();
            moveListener.onDragCompleted(currentTags);
        } else {
            Log.e("TagsAdapter", "moveListener is null! Cannot call onDragCompleted()");
        }
    }

    @NonNull
    @Override
    public TagViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemTagManagementBinding binding = ItemTagManagementBinding.inflate(inflater, parent, false);
        return new TagViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TagViewHolder holder, int position) {
        Tag tag = getItem(position);
        holder.bind(tag, position);
    }

    public class TagViewHolder extends RecyclerView.ViewHolder {
        private final ItemTagManagementBinding binding;

        public TagViewHolder(@NonNull ItemTagManagementBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Tag tag, int position) {
            binding.setTag(tag);
            binding.executePendingBindings();

            // Set click listeners
            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onTagClick(tag, position);
                }
            });

            binding.getRoot().setOnLongClickListener(v -> {
                if (clickListener != null && !SystemTagsManager.isAddTag(tag)) {
                    clickListener.onTagLongClick(tag, v);
                    return true;
                }
                return false;
            });

            // Options button listener (only for regular tags)
            if (!SystemTagsManager.isAddTag(tag)) {
                binding.optionsButton.setOnClickListener(v -> {
                    if (clickListener != null) {
                        clickListener.onOptionsClick(tag, v);
                    }
                });
            }
        }
    }

    private static class TagDiffCallback extends DiffUtil.ItemCallback<Tag> {
        @Override
        public boolean areItemsTheSame(@NonNull Tag oldItem, @NonNull Tag newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Tag oldItem, @NonNull Tag newItem) {
            return oldItem.getNameTag().equals(newItem.getNameTag()) &&
                   oldItem.getVisibility() == newItem.getVisibility() &&
                   oldItem.getSystemAction() == newItem.getSystemAction() && oldItem.getPosition() == newItem.getPosition();
        }
    }
}