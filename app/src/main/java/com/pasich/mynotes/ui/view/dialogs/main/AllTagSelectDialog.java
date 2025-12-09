package com.pasich.mynotes.ui.view.dialogs.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ScrollView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Tag;

import java.util.List;

public class AllTagSelectDialog {

    public static void show(Context ctx, List<Tag> tags, Callback callback) {

        ScrollView root = (ScrollView) LayoutInflater.from(ctx)
                .inflate(R.layout.dialog_tag_select, null);

        ChipGroup chipGroup = root.findViewById(R.id.chipGroupTags);

        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(ctx, R.style.Theme_MyNotes_Dialog)
                        .setTitle(R.string.titleTagPicker)
                        .setView(root)
                        .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss());

        AlertDialog dialog = builder.create();

        for (Tag tag : tags) {
            Chip chip = new Chip(ctx, null, com.google.android.material.R.attr.chipStyle);
            chip.setText(tag.getNameTag());
            chip.setCheckable(false);
            chip.setClickable(true);

            chip.setOnClickListener(v -> {
                callback.onTagSelected(tag);
                dialog.dismiss();
            });

            chipGroup.addView(chip);
        }

        dialog.show();
    }

    public interface Callback {
        void onTagSelected(Tag tag);
    }
}
