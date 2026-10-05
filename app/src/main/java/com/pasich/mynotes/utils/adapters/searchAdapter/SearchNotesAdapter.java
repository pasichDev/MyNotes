package com.pasich.mynotes.utils.adapters.searchAdapter;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ItemResultBinding;
import com.pasich.mynotes.utils.search.NoteSearchRanker;
import com.pasich.mynotes.utils.search.SearchHit;
import com.pasich.mynotes.utils.search.SearchText;
import dagger.hilt.android.scopes.ActivityScoped;
import javax.inject.Inject;

/**
 * RecyclerView adapter for ranked note search results. The part of the title and of the preview
 * that matched the query is marked with the theme's primary container colour.
 */
@ActivityScoped
public class SearchNotesAdapter extends ListAdapter<SearchHit, SearchNotesAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<SearchHit> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull SearchHit oldItem, @NonNull SearchHit newItem) {
                    return oldItem.note().getId() == newItem.note().getId();
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull SearchHit oldItem, @NonNull SearchHit newItem) {
                    return oldItem.sameContentAs(newItem);
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
        ItemResultBinding binding =
                ItemResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
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

        public void bind(SearchHit hit) {
            Note note = hit.note();
            binding.setNote(note);
            binding.executePendingBindings();

            // A one-letter query never searched the text, so marking a letter there would mislead.
            boolean bodySearched = hit.query().length() >= NoteSearchRanker.MIN_BODY_QUERY_LENGTH;
            setHighlighted(binding.nameNote, note.getTitle(), hit.query());
            setHighlighted(
                    binding.previewNote, note.getValuePreview(), bodySearched ? hit.query() : null);

            if (clickListener != null) {
                itemView.setOnClickListener(v -> clickListener.onClick(note, binding.itemNote));
            }
        }

        private static void setHighlighted(TextView view, String text, String query) {
            int[] range = query == null ? null : SearchText.locate(text, query);
            if (range == null) {
                view.setText(text);
                return;
            }
            SpannableString marked = new SpannableString(text);
            int background =
                    MaterialColors.getColor(
                            view, com.google.android.material.R.attr.colorPrimaryContainer);
            int foreground =
                    MaterialColors.getColor(
                            view, com.google.android.material.R.attr.colorOnPrimaryContainer);
            marked.setSpan(
                    new BackgroundColorSpan(background),
                    range[0],
                    range[1],
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            marked.setSpan(
                    new ForegroundColorSpan(foreground),
                    range[0],
                    range[1],
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            view.setText(marked);
        }
    }
}
