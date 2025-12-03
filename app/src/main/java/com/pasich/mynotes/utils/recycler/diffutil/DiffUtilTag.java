package com.pasich.mynotes.utils.recycler.diffutil;


import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.utils.recycler.TagPayloads;


import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;

@ActivityScoped
public class DiffUtilTag extends DiffUtil.ItemCallback<Tag> {

    @Inject
    public DiffUtilTag() {
    }

    @Override
    public boolean areItemsTheSame(@NonNull Tag oldItem, @NonNull Tag newItem) {
        return oldItem.getId() == newItem.getId();
    }

    @Override
    public boolean areContentsTheSame(@NonNull Tag oldItem, @NonNull Tag newItem) {
        return oldItem.getNameTag().equals(newItem.getNameTag())
                && oldItem.getVisibility() == newItem.getVisibility()
                && oldItem.getSystemAction() == newItem.getSystemAction()
                && oldItem.getSelected() == newItem.getSelected();
    }

    @Override
    public Object getChangePayload(@NonNull Tag oldItem, @NonNull Tag newItem) {
        List<String> payloads = new ArrayList<>();

        if (oldItem.getSelected() != newItem.getSelected()) {
            payloads.add(TagPayloads.SELECTED);
        }
        if (!oldItem.getNameTag().equals(newItem.getNameTag())) {
            payloads.add(TagPayloads.NAME);
        }
        if (oldItem.getVisibility() != newItem.getVisibility()) {
            payloads.add(TagPayloads.VISIBILITY);
        }
        if (oldItem.getSystemAction() != newItem.getSystemAction()) {
            payloads.add(TagPayloads.SYSTEM);
        }

        return payloads.isEmpty() ? null : payloads;
    }
}
