package com.pasich.mynotes.utils.actionPanel.tool;

import static com.pasich.mynotes.utils.actionPanel.ActionUtils.getAction;
import static com.pasich.mynotes.utils.actionPanel.ActionUtils.setAction;

import com.pasich.mynotes.data.model.TrashNote;
import com.pasich.mynotes.databinding.ItemNoteTrashBinding;
import com.pasich.mynotes.utils.adapters.baseGenericAdapter.GenericAdapter;
import com.pasich.mynotes.utils.adapters.notes.TrashAdapter;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

public class TrashNoteActionTool {

    private final ArrayList<TrashNote> ArrayChecked = new ArrayList<>();
    private final GenericAdapter tAdapter;

    @Inject
    public TrashNoteActionTool(TrashAdapter<ItemNoteTrashBinding> adapter) {
        this.tAdapter = adapter;
    }

    public ArrayList<TrashNote> getArrayChecked() {
        return this.ArrayChecked;
    }

    public int getCountCheckedItem() {
        List<TrashNote> data = tAdapter.getCurrentList();
        int count = 0;
        for (int i = 0; i < data.size(); i++) {
            count = data.get(i).getChecked() ? count + 1 : count;
        }
        return count;
    }

    public void checkedClean() {
        List<TrashNote> data = tAdapter.getCurrentList();
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getChecked()) data.get(i).setChecked(false);
            tAdapter.notifyItemChanged(i, 22);
            getArrayChecked().clear();
        }
    }

    public void isCheckedItem(TrashNote note) {
        if (!getArrayChecked().contains(note)) getArrayChecked().add(note);
        else getArrayChecked().remove(note);
        if (!(getAction())) setAction(true);
    }

    public boolean isCheckedItemFalse(TrashNote note) {
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
