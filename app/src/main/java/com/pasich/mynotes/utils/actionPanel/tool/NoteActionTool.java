package com.pasich.mynotes.utils.actionPanel.tool;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ItemNoteBinding;
import com.pasich.mynotes.utils.actionPanel.ActionUtils;
import com.pasich.mynotes.utils.adapters.baseGenericAdapter.GenericAdapter;
import com.pasich.mynotes.utils.adapters.notes.NoteAdapter;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

public class NoteActionTool {

    private final ArrayList<Note> ArrayChecked = new ArrayList<>();
    private final GenericAdapter mAdapter;
    private final ActionUtils actionUtils;

    @Inject
    public NoteActionTool(NoteAdapter<ItemNoteBinding> adapter, ActionUtils actionUtils) {
        this.mAdapter = adapter;
        this.actionUtils = actionUtils;
    }

    public ArrayList<Note> getArrayChecked() {
        return this.ArrayChecked;
    }

    public int getCountCheckedItem() {
        List<Note> data = mAdapter.getCurrentList();
        int count = 0;
        for (int i = 0; i < data.size(); i++) {
            count = data.get(i).getChecked() ? count + 1 : count;
        }
        return count;
    }

    public void checkedClean() {
        List<Note> data = (List<Note>) mAdapter.getCurrentList();
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getChecked()) data.get(i).setChecked(false);
            mAdapter.notifyItemChanged(i, 22);
            getArrayChecked().clear();
        }
    }

    public void isCheckedItem(Note note) {
        if (!getArrayChecked().contains(note)) getArrayChecked().add(note);
        else getArrayChecked().remove(note);
        if (!actionUtils.getAction()) actionUtils.setAction(true);
    }

    public boolean isCheckedItemFalse(Note note) {
        if (getCountCheckedItem() == 0) {
            getArrayChecked().clear();
            actionUtils.setAction(false);
            return false;
        } else {
            getArrayChecked().remove(note);
            return true;
        }
    }

    public void cleanup() {
        ArrayChecked.clear();
        actionUtils.setAction(false);
    }

}
