package com.pasich.mynotes.utils.recycler.diffutil;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.pasich.mynotes.data.model.Note;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;

@ActivityScoped
public class DiffUtilNote extends DiffUtil.ItemCallback<Note> {

    @Inject
    public DiffUtilNote() {
    }

    @Override
    public boolean areItemsTheSame(@NonNull Note oldItem, @NonNull Note newItem) {
        // Якщо один з елементів має ID = 0, порівнюємо за датою та вмістом
        if (oldItem.getId() == 0 || newItem.getId() == 0) {
            return oldItem.getDate() == newItem.getDate() && 
                   oldItem.getTitle().equals(newItem.getTitle()) &&
                   oldItem.getValue().equals(newItem.getValue());
        }
        return oldItem.getId() == newItem.getId();
    }

    @Override
    public boolean areContentsTheSame(@NonNull Note oldItem, @NonNull Note newItem) {
        return oldItem.getId() == newItem.getId() &&
               oldItem.getTitle().equals(newItem.getTitle()) && 
               oldItem.getValue().equals(newItem.getValue()) && 
               oldItem.getTag().equals(newItem.getTag()) &&
               oldItem.getDate() == newItem.getDate() && oldItem.getBackground().equals(newItem.getBackground());
    }
}