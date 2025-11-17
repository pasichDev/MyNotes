package com.pasich.mynotes.utils.databinding;

import android.view.View;
import android.widget.TextView;

import androidx.databinding.BindingAdapter;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.utils.FormattedDataUtil;
import com.pasich.mynotes.utils.noteEditor.models.ParsedNote;

public class NoteBindingAdapters {

    @BindingAdapter("dataNote")
    public static void setDataNote(TextView textView, Note note) {
        if (note != null && note.getDate() > 0) {
            textView.setText(
                    textView.getContext().getString(R.string.lastDateEditNote,  FormattedDataUtil.lastDayEditNote(note.getDate()))
            );
            textView.setVisibility(View.VISIBLE);
        } else {
            textView.setText("");
            textView.setVisibility(View.GONE);
        }
    }

    @BindingAdapter("noteMediaCount")
    public static void setNoteMediaCount(TextView textView, Note note) {
        int countMedia = ParsedNote.parseAttachmentsJson(note.getAttachments()).size();
        if (countMedia > 0) {
            textView.setText(
                    textView.getContext().getString(R.string.countMediaNote, countMedia)
            );
            textView.setVisibility(View.VISIBLE);
        } else {
            textView.setText("");
            textView.setVisibility(View.GONE);
        }
    }
}
