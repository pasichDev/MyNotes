package com.pasich.mynotes.utils.adapters.searchAdapter;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.pasich.mynotes.data.model.IndexFilter;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ItemResultBinding;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;

@ActivityScoped
public class SearchNotesAdapter extends ListAdapter<Note, SearchNotesAdapter.ViewHolder> {

    private List<Note> defaultListNotes = new ArrayList<>();
    private List<IndexFilter> indexValue = new ArrayList<>();
    private SetItemClickListener onItemClickListener;
    private String textSearch;

    @Inject
    public SearchNotesAdapter() {
        super(DIFF_CALLBACK);
    }

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

    public void setItemClickListener(SetItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    public void setDefaultListNotes(List<Note> defaultListNotes) {
        this.defaultListNotes = defaultListNotes != null ? defaultListNotes : new ArrayList<>();
    }

    public List<Note> getData() {
        return getCurrentList();
    }

    @NonNull
    @Override
    public SearchNotesAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemResultBinding binding = ItemResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchNotesAdapter.ViewHolder holder, int position) {
        Note note = getItem(position);
        holder.binding.setNote(note);

        final int colorSpannable = MaterialColors.getColor(holder.itemView.getContext(), com.google.android.material.R.attr.colorSurfaceVariant, Color.GRAY);

        Spannable titleNote = highlightMatch(note.getTitle(), note.getId(), true, colorSpannable);
        Spannable valueNote = highlightMatch(note.getValue(), note.getId(), false, colorSpannable);

        holder.binding.nameNote.setText(titleNote);
        holder.binding.previewNote.setText(valueNote);
        holder.binding.tagNote.setText(note.getTag());

        if (onItemClickListener != null) {
            holder.itemView.setOnClickListener(v ->
                    onItemClickListener.onClick(note.getId(), holder.binding.itemNote)
            );
        }
    }

    private Spannable highlightMatch(String text, int noteId, boolean isTitle, int color) {
        Spannable spannable = new SpannableString(text);
        for (IndexFilter filter : indexValue) {
            if (filter.getIdNote() == noteId) {
                int start = isTitle ? filter.getIndexTitle() : filter.getIndexValue();
                if (start != -1 && textSearch != null) {
                    spannable.setSpan(new BackgroundColorSpan(color), start, start + textSearch.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        return spannable;
    }

    public void filter(String text) {
        if (text == null || text.trim().isEmpty()) {
            cleanResult();
            return;
        }

        textSearch = text.trim().toLowerCase();
        ArrayList<Note> filteredNotes = new ArrayList<>();
        ArrayList<IndexFilter> indices = new ArrayList<>();

        for (Note note : defaultListNotes) {
            String title = note.getTitle().toLowerCase();
            String content = note.getValue().toLowerCase();

            int titleIndex = title.indexOf(textSearch);
            int contentIndex = content.indexOf(textSearch);

            boolean found = false;
            if (titleIndex != -1) {
                indices.add(new IndexFilter(note.id, titleIndex, -1));
                found = true;
            }
            if (contentIndex != -1) {
                indices.add(new IndexFilter(note.id, -1, contentIndex));
                found = true;
            }

            if (found) {
                filteredNotes.add(note);
            }
        }

        if (filteredNotes.isEmpty()) {
            cleanResult();
        } else {
            this.indexValue = indices;
            submitList(filteredNotes);
        }
    }

    public void cleanResult() {
        indexValue.clear();
        submitList(new ArrayList<>());
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemResultBinding binding;

        public ViewHolder(ItemResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Note note) {
            binding.nameNote.setText(note.getTitle());
            binding.previewNote.setText(note.getValue());
        }
    }
}
