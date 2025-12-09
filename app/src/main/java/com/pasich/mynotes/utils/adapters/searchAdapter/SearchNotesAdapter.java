package com.pasich.mynotes.utils.adapters.searchAdapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ItemResultBinding;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;


@ActivityScoped
public class SearchNotesAdapter extends ListAdapter<Note, SearchNotesAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<Note> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Note oldItem, @NonNull Note newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @SuppressLint("DiffUtilEquals")
        @Override
        public boolean areContentsTheSame(@NonNull Note oldItem, @NonNull Note newItem) {
            return oldItem.equals(newItem);
        }
    };

    private SetItemClickListener onItemClickListener;

    @Inject
    public SearchNotesAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setItemClickListener(SetItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemResultBinding binding = ItemResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding, onItemClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemResultBinding binding;
        private final SetItemClickListener clickListener;

        public ViewHolder(ItemResultBinding binding, SetItemClickListener clickListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.clickListener = clickListener;
        }

        public void bind(Note note) {
            binding.setNote(note);
            binding.executePendingBindings();

            if (clickListener != null) {
                itemView.setOnClickListener(v ->
                        clickListener.onClick(note, binding.itemNote)
                );
            }
        }
    }
}
