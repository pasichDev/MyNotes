package com.pasich.mynotes.utils.databinding;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.databinding.BindingAdapter;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.extendedEditor.models.ParsedNote;
import com.pasich.mynotes.utils.FormattedDataUtil;

public class NoteBindingAdapters {

    @BindingAdapter("dataNote")
    public static void setDataNote(TextView textView, Note note) {
        if (note != null && note.getDate() > 0) {
            textView.setText(
                    textView.getContext().getString(R.string.lastDateEditNote, FormattedDataUtil.lastDayEditNote(note.getDate()))
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


    @BindingAdapter("noteStrokeColor")
    public static void setNoteStrokeColor(MaterialCardView card, Note note) {
        if (note == null) return;

        Context ctx = card.getContext();
        int color;

        if (note.getChecked()) {
            // вибране
            color = ctx.getColor(R.color.item_bindig_note_primary);

        } else if (note.isAttachments()) {
            // нотатка з розширеним редактором
            color = ctx.getColor(R.color.item_bindig_note_extended_stroke);

        } else {
            // звичайна
            color = ctx.getColor(R.color.item_bindig_note_surface_variant);
        }

        card.setStrokeColor(color);
    }

    @BindingAdapter("noteBottomPadding")
    public static void setNoteBottomPadding(View view, Note note) {
        if (note == null) return;

        boolean hasExtras = !note.getTag().isEmpty() || note.isAttachments();

        int padding = view.getContext().getResources().getDimensionPixelSize(
                hasExtras ? R.dimen.marginItemsNoteChip : R.dimen.marginItemsNote
        );

        view.setPadding(
                view.getPaddingLeft(),
                view.getPaddingTop(),
                view.getPaddingRight(),
                padding
        );
    }

    @BindingAdapter("dynamicShapeTop")
    public static void setDynamicShapeTop(MaterialCardView card, boolean needTop) {
        int style = needTop
                ? R.style.ShapeAppearance_SettingsCard_Top
                : R.style.ShapeAppearance_SettingsCard_Base;
        card.setShapeAppearanceModel(
                ShapeAppearanceModel.builder(card.getContext(), style, 0).build()
        );
    }

    @BindingAdapter("dynamicShapeBottom")
    public static void setDynamicShapeBottom(MaterialCardView card, boolean needBottom) {
        int style = needBottom
                ? R.style.ShapeAppearance_SettingsCard_Base : R.style.ShapeAppearance_SettingsCard_Bottom;
        card.setShapeAppearanceModel(
                ShapeAppearanceModel.builder(card.getContext(), style, 0).build()
        );
    }

    @BindingAdapter("paddingVerticalDynamic")
    public static void setDynamicVerticalPadding(View view, Note note) {
        if (note == null) return;

        int paddingValue;

        if (note.getTitle().isEmpty() && note.getValuePreview().isEmpty()) {
            paddingValue = view.getContext().getResources().getDimensionPixelSize(R.dimen.marginItemsNote);
        } else {
            paddingValue = view.getContext().getResources().getDimensionPixelSize(R.dimen.marginItemsNoteBottomCard);
        }

        view.setPadding(
                view.getPaddingLeft(),
                paddingValue,
                view.getPaddingRight(),
                paddingValue
        );
    }
}
