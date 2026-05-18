package com.pasich.mynotes.ui.view.dialogs.tasks;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pasich.mynotes.R;

public class AddTaskDialog {

    public interface Callback {
        void onAdd(String title, String description, int categoryId);
    }

    public static void show(Context context, int categoryId, Callback callback) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_add_task, null);
        EditText input = view.findViewById(R.id.add_task_input);
        EditText descInput = view.findViewById(R.id.add_task_desc_input);

        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.tasks_add))
                .setView(view)
                .setPositiveButton(
                        android.R.string.ok,
                        (d, w) -> {
                            String text = input.getText().toString().trim();
                            if (!text.isEmpty()) {
                                String desc = descInput.getText().toString().trim();
                                callback.onAdd(text, desc.isEmpty() ? null : desc, categoryId);
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();

        input.requestFocus();
    }
}
