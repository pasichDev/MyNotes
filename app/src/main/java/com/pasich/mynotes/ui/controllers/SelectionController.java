package com.pasich.mynotes.ui.controllers;


import android.view.View;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ActionPanelBinding;
import com.pasich.mynotes.utils.adapters.notes.NoteAdapter;
import com.pasich.mynotes.utils.recycler.payloads.NotePayloads;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SelectionController {

    private final HashSet<Integer> selectedIds = new HashSet<>();
    private final NoteAdapter adapter;
    private final ActionPanelBinding panel;

    private boolean selectionMode = false;
    private Listener listener;

    public SelectionController(NoteAdapter adapter, View rootView) {
        this.adapter = adapter;
        this.panel = ActionPanelBinding.bind(rootView.findViewById(R.id.actionInclude));

        initActions();
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    private void initActions() {
        panel.actionClose.setOnClickListener(v -> clearSelection());
        panel.actionDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteRequested();
        });
        panel.actionShare.setOnClickListener(v -> {
            if (listener != null) listener.onShareRequested();
        });
        panel.actionRestore.setOnClickListener(v -> {
            if (listener != null) listener.onRestoreRequested();
        });
        panel.actionClose.setOnClickListener(v -> clearSelection());

    }

    public boolean isInSelectionMode() {
        return selectionMode;
    }

    public void setPanelMode(Mode mode) {
        switch (mode) {
            case NORMAL:
                panel.actionShare.setVisibility(View.VISIBLE);
                panel.actionDelete.setVisibility(View.VISIBLE);
                panel.actionRestore.setVisibility(View.GONE);
                break;

            case RESTORE:
                panel.actionShare.setVisibility(View.GONE);
                panel.actionDelete.setVisibility(View.GONE);
                panel.actionRestore.setVisibility(View.VISIBLE);
                break;
        }
    }

    public void startSelection(Note note) {
        selectionMode = true;
        toggle(note);
        showPanel(true);
        notifyListener();
    }

    public void toggle(Note note) {
        int id = note.getId();

        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }

        updateNoteVisualState(id);

        // If there are no more selections after toggling, exit the mode
        if (selectedIds.isEmpty()) {
            clearSelection();
            return;
        }

        // update count ui
        if (panel != null) {
            int count = selectedIds.size();
            panel.selectedCount.setText(
                    panel.getRoot().getResources().getQuantityString(
                            R.plurals.selected_count,
                            count,
                            count
                    )
            );
        }
        notifyListener();
    }


    public void clearSelection() {
        if (!selectionMode) return;

        List<Integer> ids = new ArrayList<>(selectedIds);
        selectedIds.clear();

        for (int id : ids) {
            updateNoteVisualState(id);
        }

        selectionMode = false;
        showPanel(false);
        notifyListener();
    }

    public List<Note> getSelectedNotes() {
        List<Note> result = new ArrayList<>();
        List<Note> data = adapter.getCurrentList();

        for (Note n : data) {
            if (selectedIds.contains(n.getId())) {
                result.add(n);
            }
        }
        return result;
    }

    private void updateNoteVisualState(int id) {
        List<Note> list = adapter.getCurrentList();

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == id) {
                adapter.notifyItemChanged(i, NotePayloads.PAYLOAD_SELECTION);
                return;
            }
        }
    }


    private void showPanel(boolean show) {
        panel.getRoot().setVisibility(show ? View.VISIBLE : View.GONE);
        if (listener != null) listener.onSelectionModeChanged(show);
    }

    private void notifyListener() {
        if (listener != null)
            listener.onSelectionCountChanged(selectedIds.size());
    }

    public void cleanup() {
        panel.actionClose.setOnClickListener(null);
        panel.actionDelete.setOnClickListener(null);
        panel.actionShare.setOnClickListener(null);
        panel.actionRestore.setOnClickListener(null);

        clearSelectionInternal();

        panel.getRoot().setVisibility(View.GONE);

        listener = null;
    }

    private void clearSelectionInternal() {
        if (selectedIds.isEmpty()) return;

        List<Integer> ids = new ArrayList<>(selectedIds);
        selectedIds.clear();

        for (int id : ids) {
            updateNoteVisualState(id);
        }

        selectionMode = false;
    }

    public boolean isSelected(int id) {
        return selectedIds.contains(id);
    }

    public enum Mode {
        NORMAL,   // delete + share
        RESTORE   // restore only
    }

    public interface Listener {
        default void onSelectionCountChanged(int ignoredCount) {

        }

        void onSelectionModeChanged(boolean active);

        default void onDeleteRequested() {

        }

        default void onShareRequested() {

        }

        default void onRestoreRequested() {

        }

    }

}
