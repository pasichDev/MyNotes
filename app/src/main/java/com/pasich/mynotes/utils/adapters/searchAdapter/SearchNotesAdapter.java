package com.pasich.mynotes.utils.adapters.searchAdapter;


import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.IndexFilter;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ItemResultBinding;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;


@ActivityScoped
public class SearchNotesAdapter extends RecyclerView.Adapter<SearchNotesAdapter.ViewHolder> {

    private List<Note> defaultListNotes = new ArrayList<>();
    private List<Note> listNotes = new ArrayList<>();
    private List<IndexFilter> indexValue = new ArrayList<>();
    private SetItemClickListener mOnItemClickListener;
    private String textSearch;

    @Inject
    public SearchNotesAdapter() {
    }

    public void setItemClickListener(SetItemClickListener onItemClickListener) {
        this.mOnItemClickListener = onItemClickListener;
    }


    public void setDefaultListNotes(List<Note> defaultListNotes) {
        this.defaultListNotes = defaultListNotes;
    }

    @Override
    public int getItemCount() {
        return (null != listNotes ? listNotes.size() : 0);
    }

    public List<Note> getData() {
        return this.listNotes;
    }


    @NonNull
    @Override
    public SearchNotesAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder view = new SearchNotesAdapter.ViewHolder(ItemResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));

        if (mOnItemClickListener != null) {
            view.itemView.setOnClickListener(v -> mOnItemClickListener.onClick(getData().get(view.getAdapterPosition()).getId(), view.ItemBinding.itemNote));
        }

        return view;
    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final Note note = listNotes.get(position);
        holder.ItemBinding.setNote(listNotes.get(position));

        final int colorSpannable = MaterialColors.getColor(holder.itemView.getContext(), R.attr.colorSurfaceVariant, Color.GRAY);
        Spannable titleNote = new SpannableString(note.getTitle());
        Spannable valueNote = new SpannableString(note.getValue());

        for (IndexFilter filter : indexValue) {

            if (filter.getIdNote() == listNotes.get(position).getId()) {
                if (filter.getIndexTitle() != -1) {
                    titleNote.setSpan(new BackgroundColorSpan(colorSpannable)
                            , filter.getIndexTitle(), filter.getIndexTitle() + textSearch.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                if (filter.getIndexValue() != -1) {
                    valueNote.setSpan(new BackgroundColorSpan(
                            colorSpannable), filter.getIndexValue(), filter.getIndexValue() + textSearch.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

            }
        }

        holder.ItemBinding.previewNote.setText(valueNote);
        holder.ItemBinding.nameNote.setText(titleNote);
        holder.ItemBinding.tagNote.setText(note.getTag());
    }

    public void filter(String text) {
        ArrayList<Note> newFilter = new ArrayList<>();
        ArrayList<IndexFilter> indexFilter = new ArrayList<>();
        for (Note item : defaultListNotes) {
            int indexTitle = item.getTitle().toLowerCase().indexOf(text.toLowerCase());
            int indexValue = item.getValue().toLowerCase().indexOf(text.toLowerCase());
            int countArrays = indexFilter.size();
            while (indexTitle != -1) {
                indexFilter.add(new IndexFilter(item.id, indexTitle, -1));
                indexTitle = item.getTitle().toLowerCase().indexOf(text.toLowerCase(), indexTitle + 1);
            }

            while (indexValue != -1) {
                indexFilter.add(new IndexFilter(item.id, -1, indexValue));
                indexValue = item.getValue().toLowerCase().indexOf(text.toLowerCase(), indexValue + 1);
            }


            if (indexFilter.size() != countArrays) {
                newFilter.add(item);
            }
        }
        if (newFilter.isEmpty()) {
            cleanResult();
        } else {

            filterList(newFilter, text, indexFilter);
        }

    }


    public void filterList(ArrayList<Note> newListFilter, String textSearch, ArrayList<IndexFilter> indexValue) {
        this.listNotes = newListFilter;
        this.indexValue = indexValue;
        this.textSearch = textSearch;
        notifyDataSetChanged();

    }

    public void cleanResult() {
        if (listNotes.size() >= 1) {
            listNotes.clear();
            indexValue.clear();
            notifyDataSetChanged();
        }
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemResultBinding ItemBinding;

        ViewHolder(ItemResultBinding binding) {
            super(binding.getRoot());
            ItemBinding = binding;
        }
    }
}
