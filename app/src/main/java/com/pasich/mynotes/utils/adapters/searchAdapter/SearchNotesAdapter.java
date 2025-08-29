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
        if (text == null || text.trim().isEmpty()) {
            cleanResult();
            return;
        }
        
        // Нормалізуємо текст пошуку
        String searchText = text.toLowerCase().trim();
        
        // Ініціалізуємо колекції з початковою ємністю для оптимізації
        ArrayList<Note> filteredNotes = new ArrayList<>();
        ArrayList<IndexFilter> indices = new ArrayList<>();
        
        // Проходимо по всіх нотатках один раз
        for (Note note : defaultListNotes) {
            String title = note.getTitle().toLowerCase();
            String content = note.getValue().toLowerCase();
            boolean found = false;
            
            // Пошук в заголовку
            int titleIndex = title.indexOf(searchText);
            if (titleIndex != -1) {
                indices.add(new IndexFilter(note.id, titleIndex, -1));
                found = true;
            }
            
            // Пошук в контенті
            int contentIndex = content.indexOf(searchText);
            if (contentIndex != -1) {
                indices.add(new IndexFilter(note.id, -1, contentIndex));
                found = true;
            }
            
            // Додаємо нотатку тільки якщо знайдено збіг
            if (found) {
                filteredNotes.add(note);
            }
        }
        
        // Обробляємо результат пошуку
        if (filteredNotes.isEmpty()) {
            cleanResult();
        } else {
            filterList(filteredNotes, text, indices);
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
