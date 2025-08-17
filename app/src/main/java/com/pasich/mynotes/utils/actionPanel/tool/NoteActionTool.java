package com.pasich.mynotes.utils.actionPanel.tool;

import static com.pasich.mynotes.utils.actionPanel.ActionUtils.getAction;
import static com.pasich.mynotes.utils.actionPanel.ActionUtils.setAction;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ItemNoteBinding;
import com.pasich.mynotes.utils.adapters.baseGenericAdapter.GenericAdapter;
import com.pasich.mynotes.utils.adapters.notes.NoteAdapter;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

public class NoteActionTool {

    @Inject
    public NoteActionTool(NoteAdapter<ItemNoteBinding> adapter) {
        this.mAdapter = adapter;
    }

    private final ArrayList<Note> ArrayChecked = new ArrayList<>();
    private final GenericAdapter mAdapter;

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
        if (!(getAction())) setAction(true);
    }

    public boolean isCheckedItemFalse(Note note) {
        if (getCountCheckedItem() == 0) {
            getArrayChecked().clear();
            setAction(false);
            return false;
        } else {
            getArrayChecked().remove(note);
            return true;
        }
    }

}
