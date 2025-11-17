package com.pasich.mynotes.utils.databinding;

import android.annotation.SuppressLint;
import android.widget.TextView;

import androidx.databinding.BindingAdapter;
public class AttachmentsBindingAdapter {
    @SuppressLint("DefaultLocale")
    @BindingAdapter("formatFileSize")
    public static void bindFileSize(TextView view, long size) {
        String text;

        if (size >= 1024 * 1024) {
            text = String.format("%.1f MB", size / (1024f * 1024f));
        } else {
            text = String.format("%.1f KB", size / 1024f);
        }

        view.setText(text);
    }
}