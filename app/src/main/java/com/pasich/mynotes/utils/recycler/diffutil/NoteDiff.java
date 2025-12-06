package com.pasich.mynotes.utils.recycler.diffutil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.utils.recycler.payloads.NotePayload;

import dagger.hilt.android.scopes.ActivityScoped;

@ActivityScoped
public class NoteDiff extends DiffUtil.ItemCallback<Note> {

    @Override
    public boolean areItemsTheSame(@NonNull Note old, @NonNull Note ne) {
        return old.getId() == ne.getId();
    }

    @Override
    public boolean areContentsTheSame(@NonNull Note old, @NonNull Note ne) {
        return old.equals(ne);
    }

    @Nullable
    @Override
    public Object getChangePayload(@NonNull Note oldItem, @NonNull Note newItem) {

        if (oldItem.getChecked() != newItem.getChecked())
            return NotePayload.CHECK_CHANGED;

        if (!oldItem.getTitle().equals(newItem.getTitle()) ||
                !oldItem.getValuePreview().equals(newItem.getValuePreview()))
            return NotePayload.CONTENT_CHANGED;

        return null;
    }
}
