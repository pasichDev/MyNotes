package com.pasich.mynotes.utils.databinding;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.databinding.BindingAdapter;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.extendedEditor.models.ParsedNote;
import com.pasich.mynotes.utils.FormattedDataUtil;

public class NoteBindingAdapters {
    @BindingAdapter("cardBackgroundColorActivated")
    public static void setCardBackgroundColorActivated(MaterialCardView view, boolean activated) {
        Context ctx = view.getContext();

        int color = activated
                ? ctx.getColor(R.color.item_bindig_note_surface_variant)
                : ctx.getColor(R.color.item_bindig_note_surface);

        view.setCardBackgroundColor(color);
    }

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

        if (card.isActivated()) {
            color = ctx.getColor(R.color.item_bindig_note_primary);
        } else if (note.isAttachments()) {
            color = ctx.getColor(R.color.item_bindig_note_extended_stroke);
        } else {
            color = ctx.getColor(R.color.item_bindig_note_surface_variant);
        }


        card.setStrokeColor(color);
    }

    @BindingAdapter("noteBottomPadding")
    public static void setNoteBottomPadding(View view, Note note) {
        if (note == null) return;

        boolean hasExtras = !note.getTag().isEmpty() || note.isAttachments() || note.hasReminder();

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

    @BindingAdapter("reminderText")
    public static void setReminderText(TextView textView, Note note) {
        if (note == null || !note.hasReminder()) {
            textView.setVisibility(View.GONE);
            return;
        }

        Context ctx = textView.getContext();
        Calendar now = Calendar.getInstance();
        Calendar rem = Calendar.getInstance();
        rem.setTimeInMillis(note.getReminderTime());

        boolean isToday = now.get(Calendar.DATE) == rem.get(Calendar.DATE)
                && now.get(Calendar.MONTH) == rem.get(Calendar.MONTH)
                && now.get(Calendar.YEAR) == rem.get(Calendar.YEAR);

        Calendar tomorrowCal = Calendar.getInstance();
        tomorrowCal.add(Calendar.DATE, 1);
        boolean isTomorrow = tomorrowCal.get(Calendar.DATE) == rem.get(Calendar.DATE)
                && tomorrowCal.get(Calendar.MONTH) == rem.get(Calendar.MONTH)
                && tomorrowCal.get(Calendar.YEAR) == rem.get(Calendar.YEAR);

        Calendar weekEnd = Calendar.getInstance();
        weekEnd.add(Calendar.DATE, 7);

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String text;

        if (isToday) {
            text = timeFormat.format(rem.getTime());
        } else if (isTomorrow) {
            text = ctx.getString(R.string.reminder_chip_tomorrow, timeFormat.format(rem.getTime()));
        } else if (rem.before(weekEnd)) {
            SimpleDateFormat dayFmt = new SimpleDateFormat("EEE HH:mm", Locale.getDefault());
            text = dayFmt.format(rem.getTime());
        } else {
            SimpleDateFormat dateFmt = new SimpleDateFormat("d MMM", Locale.getDefault());
            text = dateFmt.format(rem.getTime());
        }

        textView.setText(text);
        textView.setVisibility(View.VISIBLE);
    }

    @BindingAdapter("bindNoteItemTop")
    public static void bindNoteItemTop(MaterialCardView tv, Note note) {
        if (note == null) {
            tv.setVisibility(View.GONE);
            return;
        }

        String title = note.getTitle() != null ? note.getTitle().trim() : "";
        String value = note.getValue() != null ? note.getValue().trim() : "";

        boolean isGone = title.isEmpty() && value.isEmpty() && note.isAttachments();

        if (isGone) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setVisibility(View.VISIBLE);
        }
    }

}
