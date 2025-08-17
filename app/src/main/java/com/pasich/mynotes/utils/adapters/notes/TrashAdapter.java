package com.pasich.mynotes.utils.adapters.notes;

import androidx.annotation.NonNull;
import androidx.databinding.ViewDataBinding;

import com.pasich.mynotes.data.model.TrashNote;
import com.pasich.mynotes.utils.adapters.baseGenericAdapter.GenericAdapter;
import com.pasich.mynotes.utils.adapters.baseGenericAdapter.GenericAdapterCallback;
import com.pasich.mynotes.utils.recycler.diffutil.DiffUtilTrash;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

public class TrashAdapter<VM extends ViewDataBinding> extends GenericAdapter<TrashNote, VM> {


    @Inject
    public TrashAdapter(@NonNull DiffUtilTrash diffCallback, int layoutId, GenericAdapterCallback<VM, TrashNote> bindingInterface) {
        super(diffCallback, layoutId, bindingInterface);
    }

    public void sortListTrash(List<TrashNote> notesList) {
        Collections.sort(notesList, (e1, e2) -> Long.compare(e2.getDate(), e1.getDate()));
        submitList(notesList);
    }
}
