package com.pasich.mynotes.extendedEditor.view;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pasich.mynotes.R;

public final class CopyTextDialog {

    private CopyTextDialog() {
        // no instance
    }

    public static void show(Context context, String title, String text) {
        if (context == null || text == null) return;

        ScrollView scrollView = getScrollView(context, text);

        int verticalInset = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                20,
                context.getResources().getDisplayMetrics()
        );

        new MaterialAlertDialogBuilder(context)
                .setTitle(title != null ? title : context.getString(R.string.copy_content))
                .setView(
                        scrollView,
                        0,
                        verticalInset,
                        0,
                        verticalInset
                )
                .setPositiveButton(R.string.copy_all, (d, w) -> {
                    ClipboardManager clipboard =
                            (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

                    if (clipboard != null) {
                        clipboard.setPrimaryClip(
                                ClipData.newPlainText("text", text)
                        );
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();

    }

    @NonNull
    private static ScrollView getScrollView(Context context, String text) {
        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setTextIsSelectable(true);
        textView.setTextSize(15);
        textView.setPadding(32, 32, 32, 32);
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorSurface,
                tv,
                true
        );
        textView.setBackgroundColor(tv.data);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (context.getResources().getDisplayMetrics().heightPixels * 0.7f)
        ));
        scrollView.addView(textView);
        return scrollView;
    }
}
