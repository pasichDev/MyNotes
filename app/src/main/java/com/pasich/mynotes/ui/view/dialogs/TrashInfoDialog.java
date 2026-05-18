package com.pasich.mynotes.ui.view.dialogs;

import android.content.Context;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pasich.mynotes.R;

public class TrashInfoDialog {

    public static void show(Context ctx) {
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.trash_info_title)
                .setMessage(R.string.trash_info_message)
                .setPositiveButton(R.string.trash_info_ok, (d, w) -> d.dismiss())
                .show();
    }
}
