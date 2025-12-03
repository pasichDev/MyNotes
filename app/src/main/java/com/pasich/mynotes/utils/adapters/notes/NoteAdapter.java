package com.pasich.mynotes.utils.adapters.notes;

import androidx.annotation.NonNull;
import androidx.databinding.ViewDataBinding;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.utils.adapters.baseGenericAdapter.GenericAdapter;
import com.pasich.mynotes.utils.adapters.baseGenericAdapter.GenericAdapterCallback;
import com.pasich.mynotes.utils.recycler.diffutil.DiffUtilNote;


import javax.inject.Inject;

public class NoteAdapter<VM extends ViewDataBinding> extends GenericAdapter<Note, VM> {

    @Inject
    public NoteAdapter(@NonNull DiffUtilNote diffCallback, int layoutId, GenericAdapterCallback<VM, Note> bindingInterface) {
        super(diffCallback, layoutId, bindingInterface);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
    }

}
