package com.pasich.mynotes.ui.view.dialogs.tasks;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pasich.mynotes.R;

public class AddCategoryDialog {

    public interface Callback {
        void onAdd(String name, String colorHex);
    }

    // Default color palette
    private static final String[] COLORS = {
        "#6750A4", "#B3261E", "#386A20", "#0061A4", "#984061", "#006A6B"
    };

    public static void show(Context context, Callback callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_add_category, null);
        EditText input = view.findViewById(R.id.add_category_input);

        // Use first color as default
        final String[] selectedColor = {COLORS[0]};

        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.tasks_add_category))
                .setView(view)
                .setPositiveButton(
                        android.R.string.ok,
                        (d, w) -> {
                            String name = input.getText().toString().trim();
                            if (!name.isEmpty()) callback.onAdd(name, selectedColor[0]);
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();

        input.requestFocus();
    }
}
