package com.pasich.mynotes.utils.adapters.notes;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ItemNoteBinding;
import com.pasich.mynotes.utils.recycler.diffutil.NoteDiff;
import com.pasich.mynotes.utils.recycler.payloads.NotePayload;


import java.util.List;

import javax.inject.Inject;


public class NoteAdapter extends ListAdapter<Note, NoteAdapter.NoteHolder> {

    private OnItemClickListener<Note> listener;

    @Inject
    public NoteAdapter() {
        super(new NoteDiff());
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId();
    }

    @NonNull
    @Override
    public NoteHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNoteBinding binding = ItemNoteBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new NoteHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteHolder holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public void onBindViewHolder(
            @NonNull NoteHolder holder,
            int position,
            @NonNull List<Object> payloads
    ) {
        Note note = getItem(position);

        if (payloads.isEmpty()) {
            holder.bind(note);
            return;
        }

        for (Object payload : payloads) {
            if (payload instanceof Integer) {
                switch ((int) payload) {

                    case NotePayload.CHECK_CHANGED, NotePayload.CONTENT_CHANGED:
                        holder.binding.setNote(note);
                        holder.binding.executePendingBindings();
                        break;

                }
            }
        }
    }

    public void setOnItemClickListener(OnItemClickListener<Note> listener) {
        this.listener = listener;
    }

    class NoteHolder extends RecyclerView.ViewHolder {

        final ItemNoteBinding binding;

        NoteHolder(ItemNoteBinding b) {
            super(b.getRoot());
            this.binding = b;
        }

        void bind(Note note) {
            binding.setNote(note);
            binding.executePendingBindings();

            binding.getRoot().setOnClickListener(v ->
                    listener.onClick(getBindingAdapterPosition(), note));

            binding.getRoot().setOnLongClickListener(v -> {
                listener.onLongClick(getBindingAdapterPosition(), note);
                return true;
            });

            binding.getRoot().setTransitionName("note_" + note.getId());
        }
    }
}
