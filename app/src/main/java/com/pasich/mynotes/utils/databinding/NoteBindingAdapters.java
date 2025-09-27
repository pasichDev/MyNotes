package com.pasich.mynotes.utils.databinding;

import android.widget.TextView;

import androidx.databinding.BindingAdapter;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.utils.FormattedDataUtil;

public class NoteBindingAdapters {

    @BindingAdapter("dataNote")
    public static void setDataNote(TextView textView, Note note) {
        if (note != null && note.getDate() > 0) {
            textView.setText(FormattedDataUtil.lastDayEditNote(note.getDate()));
            textView.setVisibility(TextView.VISIBLE);
        } else {
            textView.setText("");
            textView.setVisibility(TextView.GONE);
        }
    }

}

