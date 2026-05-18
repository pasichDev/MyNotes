package com.pasich.mynotes.utils.adapters.notes;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ItemNoteBinding;
import com.pasich.mynotes.ui.controllers.SelectionController;
import com.pasich.mynotes.utils.recycler.diffutil.NoteDiff;
import com.pasich.mynotes.utils.recycler.payloads.NotePayloads;
import dagger.hilt.android.scopes.ActivityScoped;
import java.util.List;
import javax.inject.Inject;

/** RecyclerView adapter for displaying note cards with selection support. */
@ActivityScoped
public class NoteAdapter extends ListAdapter<Note, NoteAdapter.NoteHolder> {

    private OnItemClickListener<Note> listener;
    private SelectionController selectionController;

    @Inject
    public NoteAdapter() {
        super(new NoteDiff());
    }

    /** Attaches a SelectionController to enable per-item selection state rendering. */
    public void setSelectionController(SelectionController controller) {
        this.selectionController = controller;
    }

    @NonNull
    @Override
    public NoteHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNoteBinding binding =
                ItemNoteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new NoteHolder(binding);
    }

    @Override
    public void onBindViewHolder(
            @NonNull NoteHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(NotePayloads.PAYLOAD_SELECTION)) {
            holder.bindSelectionState(getItem(position));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull NoteHolder holder, int position) {
        holder.bind(getItem(position));
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

            bindSelectionState(note);

            binding.getRoot()
                    .setOnClickListener(v -> listener.onClick(getBindingAdapterPosition(), note));

            binding.getRoot()
                    .setOnLongClickListener(
                            v -> {
                                listener.onLongClick(getBindingAdapterPosition(), note);
                                return true;
                            });

            binding.getRoot().setTransitionName("note_" + note.getId());
        }

        void bindSelectionState(Note note) {

            boolean isSelected =
                    selectionController != null && selectionController.isSelected(note.getId());

            binding.setActivated(isSelected);
            binding.itemTop.setActivated(isSelected);
            binding.itemNoteBottom.setActivated(isSelected);

            binding.executePendingBindings();
        }
    }
}
