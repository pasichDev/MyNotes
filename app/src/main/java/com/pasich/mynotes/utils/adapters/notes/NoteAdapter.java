package com.pasich.mynotes.utils.adapters.notes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.AdapterListUpdateCallback;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ItemNoteBinding;
import com.pasich.mynotes.ui.controllers.SelectionController;
import com.pasich.mynotes.utils.recycler.diffutil.NoteDiff;
import com.pasich.mynotes.utils.recycler.payloads.NotePayloads;
import dagger.hilt.android.scopes.ActivityScoped;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

/**
 * RecyclerView adapter for displaying note cards with selection support.
 *
 * <p>Also carries a drag in the custom order: while a card is being dragged the adapter shows its
 * own working copy of the list, moved one step at a time as {@link
 * androidx.recyclerview.widget.ItemTouchHelper} asks, and the dropped order is then handed to the
 * differ without dispatching it again — the cards are already where it says they are, and a second
 * round of moves would send them sliding over one another.
 */
@ActivityScoped
public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteHolder> {

    /** Moves a note one place through the accessibility actions of its card. */
    public interface ReorderListener {
        /** {@code step} is -1 to move the note up the list, +1 to move it down. */
        void onMoveRequested(@NonNull Note note, int step);
    }

    private final ListUpdateCallback adapterCallback = new AdapterListUpdateCallback(this);
    private boolean muted;
    private final AsyncListDiffer<Note> differ =
            new AsyncListDiffer<>(
                    new ListUpdateCallback() {
                        @Override
                        public void onInserted(int position, int count) {
                            if (!muted) adapterCallback.onInserted(position, count);
                        }

                        @Override
                        public void onRemoved(int position, int count) {
                            if (!muted) adapterCallback.onRemoved(position, count);
                        }

                        @Override
                        public void onMoved(int fromPosition, int toPosition) {
                            if (!muted) adapterCallback.onMoved(fromPosition, toPosition);
                        }

                        @Override
                        public void onChanged(int position, int count, @Nullable Object payload) {
                            if (!muted) adapterCallback.onChanged(position, count, payload);
                        }
                    },
                    new AsyncDifferConfig.Builder<>(new NoteDiff()).build());

    private OnItemClickListener<Note> listener;
    private SelectionController selectionController;
    @Nullable private ReorderListener reorderListener;

    /** The order shown while a card is dragged; null otherwise. */
    @Nullable private List<Note> dragOrder;

    @Inject
    public NoteAdapter() {}

    /** Attaches a SelectionController to enable per-item selection state rendering. */
    public void setSelectionController(SelectionController controller) {
        this.selectionController = controller;
    }

    public void submitList(@Nullable List<Note> list) {
        differ.submitList(list);
    }

    public void submitList(@Nullable List<Note> list, @Nullable Runnable commitCallback) {
        differ.submitList(list, commitCallback);
    }

    /** The list on screen, including the working order of a drag in progress. */
    @NonNull
    public List<Note> getCurrentList() {
        return dragOrder != null
                ? Collections.unmodifiableList(dragOrder)
                : differ.getCurrentList();
    }

    @NonNull
    Note getItem(int position) {
        return dragOrder != null ? dragOrder.get(position) : differ.getCurrentList().get(position);
    }

    @Override
    public int getItemCount() {
        return dragOrder != null ? dragOrder.size() : differ.getCurrentList().size();
    }

    /** Whether a drag is in progress. */
    public boolean isDragging() {
        return dragOrder != null;
    }

    /** Starts a drag over the list as it is now shown. */
    public void beginDrag() {
        if (dragOrder == null) dragOrder = new ArrayList<>(differ.getCurrentList());
    }

    /** Moves one card one or more places during a drag. */
    public void moveDuringDrag(int from, int to) {
        if (dragOrder == null
                || from == to
                || from < 0
                || to < 0
                || from >= dragOrder.size()
                || to >= dragOrder.size()) return;
        dragOrder.add(to, dragOrder.remove(from));
        notifyItemMoved(from, to);
    }

    /**
     * Ends a drag, keeping the order it left on screen.
     *
     * @param committed runs once the list behind the adapter is the dropped order.
     */
    public void endDrag(@NonNull Runnable committed) {
        List<Note> dropped = dragOrder;
        if (dropped == null) {
            committed.run();
            return;
        }
        muted = true;
        differ.submitList(
                new ArrayList<>(dropped),
                () -> {
                    muted = false;
                    dragOrder = null;
                    committed.run();
                });
    }

    /** Turns on the "move up" and "move down" accessibility actions of every card, or off. */
    public void setReorderListener(@Nullable ReorderListener listener) {
        if (reorderListener == listener) return;
        reorderListener = listener;
        notifyItemRangeChanged(0, getItemCount(), NotePayloads.PAYLOAD_REORDER);
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
        if (!payloads.isEmpty()
                && (payloads.contains(NotePayloads.PAYLOAD_SELECTION)
                        || payloads.contains(NotePayloads.PAYLOAD_REORDER))) {
            Note note = getItem(position);
            if (payloads.contains(NotePayloads.PAYLOAD_SELECTION)) holder.bindSelectionState(note);
            if (payloads.contains(NotePayloads.PAYLOAD_REORDER)) holder.bindReorderActions(note);
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull NoteHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public void setOnItemClickListener(OnItemClickListener<Note> listener) {
        this.listener = listener;
    }

    public class NoteHolder extends RecyclerView.ViewHolder {

        final ItemNoteBinding binding;
        private int moveUpAction = View.NO_ID;
        private int moveDownAction = View.NO_ID;

        NoteHolder(ItemNoteBinding b) {
            super(b.getRoot());
            this.binding = b;
        }

        void bind(Note note) {
            binding.setNote(note);
            binding.executePendingBindings();

            bindSelectionState(note);
            bindReorderActions(note);

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

        /**
         * A handle on the cards that can be dragged, in the custom order. The first line of text
         * keeps clear of it.
         */
        void bindDragHandle(boolean shown) {
            binding.dragHandle.setVisibility(shown ? View.VISIBLE : View.GONE);
            int clear =
                    shown
                            ? itemView.getResources()
                                    .getDimensionPixelSize(R.dimen.note_drag_handle_clearance)
                            : 0;
            boolean titled = binding.nameNote.getVisibility() == View.VISIBLE;
            setEndPadding(binding.nameNote, titled ? clear : 0);
            setEndPadding(binding.previewNote, titled ? 0 : clear);
        }

        private void setEndPadding(View view, int end) {
            view.setPaddingRelative(
                    view.getPaddingStart(), view.getPaddingTop(), end, view.getPaddingBottom());
        }

        /**
         * In the custom order a card can also be moved without dragging, which is the only way for
         * someone using TalkBack or a switch.
         */
        void bindReorderActions(Note note) {
            if (moveUpAction != View.NO_ID) {
                ViewCompat.removeAccessibilityAction(itemView, moveUpAction);
                moveUpAction = View.NO_ID;
            }
            if (moveDownAction != View.NO_ID) {
                ViewCompat.removeAccessibilityAction(itemView, moveDownAction);
                moveDownAction = View.NO_ID;
            }
            ReorderListener reorder = reorderListener;
            bindDragHandle(reorder != null && !note.isPinned());
            if (reorder == null) return;
            moveUpAction =
                    ViewCompat.addAccessibilityAction(
                            itemView,
                            itemView.getContext().getString(R.string.note_move_up),
                            (view, arguments) -> {
                                reorder.onMoveRequested(note, -1);
                                return true;
                            });
            moveDownAction =
                    ViewCompat.addAccessibilityAction(
                            itemView,
                            itemView.getContext().getString(R.string.note_move_down),
                            (view, arguments) -> {
                                reorder.onMoveRequested(note, 1);
                                return true;
                            });
        }
    }
}
