package com.pasich.mynotes.ui.view.dialogs.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Tag;

import java.util.List;

public class SingleTagSelectDialog {

    public static void show(
            Context ctx,
            List<Tag> allTags,
            Callback callback
    ) {

        ScrollView root = (ScrollView) LayoutInflater.from(ctx)
                .inflate(R.layout.dialog_tag_select, null);

        TextView desc = root.findViewById(R.id.tagDialogDescription);
        desc.setVisibility(View.VISIBLE);

        ChipGroup chipGroup = root.findViewById(R.id.chipGroupTags);
        chipGroup.setSingleSelection(true);

        final String[] selectedTag = new String[1];
        selectedTag[0] = null;

        Chip clearChip = new Chip(ctx, null, com.google.android.material.R.attr.chipStyle);
        clearChip.setText(R.string.clear_tag);
        clearChip.setCheckable(true);
        clearChip.setClickable(true);


        clearChip.setOnCheckedChangeListener((v, checked) -> {
            if (checked) {
                selectedTag[0] = "";
            }
        });

        chipGroup.addView(clearChip);

        for (Tag tag : allTags) {
            Chip chip = new Chip(ctx, null, com.google.android.material.R.attr.chipStyle);
            chip.setText(tag.getNameTag());
            chip.setCheckable(true);
            chip.setClickable(true);

            if (tag.getNameTag().equals(selectedTag[0])) {
                chip.setChecked(true);
            }

            chip.setOnCheckedChangeListener((v, checked) -> {
                if (checked) {
                    selectedTag[0] = tag.getNameTag();
                }
            });

            chipGroup.addView(chip);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(ctx, R.style.Theme_MyNotes_Dialog)
                .setView(root)
                .setNegativeButton(R.string.cancel, (d, w) -> {
                    callback.onCancel();
                    d.dismiss();
                })
                .setPositiveButton(R.string.apply, (d, w) -> {
                    callback.onTagSelected(selectedTag[0]);
                    d.dismiss();
                })
                .create();

        dialog.show();
    }

    public interface Callback {
        void onTagSelected(String tagName);

        void onCancel();
    }
}
