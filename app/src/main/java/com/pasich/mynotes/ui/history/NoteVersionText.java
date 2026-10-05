package com.pasich.mynotes.ui.history;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import com.google.android.material.color.MaterialColors;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.history.NoteVersionReason;
import com.pasich.mynotes.ui.sync.SyncConflictPresentation;

/** Wording and formatting shared by the version list and the version preview. */
public final class NoteVersionText {

    private NoteVersionText() {}

    /** What a person reads as the note: its title, then its text. */
    @NonNull
    public static String readable(@NonNull String title, @NonNull String value) {
        String head = title.trim();
        String body = value.trim();
        if (head.isEmpty()) return body;
        if (body.isEmpty()) return head;
        return head + "\n" + body;
    }

    @StringRes
    public static int reasonLabel(@NonNull NoteVersionReason reason) {
        return switch (reason) {
            case PRE_SYNC -> R.string.version_reason_pre_sync;
            case PRE_RESTORE -> R.string.version_reason_pre_restore;
            case PRE_CONFLICT -> R.string.version_reason_pre_conflict;
            case AUTOSAVE -> R.string.version_reason_autosave;
        };
    }

    /** "5 minutes ago, 14:05", "Yesterday, 14:05" or a date, in the user's locale. */
    @NonNull
    public static CharSequence when(@NonNull Context context, long time) {
        return DateUtils.getRelativeDateTimeString(
                context,
                time,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.WEEK_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_MONTH);
    }

    /** The window's text with its differing range marked in the theme's primary colours. */
    @NonNull
    public static CharSequence highlighted(
            @NonNull Context context, @NonNull SyncConflictPresentation.Window window) {
        if (window.end <= window.start) return window.text;
        SpannableString text = new SpannableString(window.text);
        int container =
                MaterialColors.getColor(
                        context, com.google.android.material.R.attr.colorPrimaryContainer, 0);
        int onContainer =
                MaterialColors.getColor(
                        context, com.google.android.material.R.attr.colorOnPrimaryContainer, 0);
        text.setSpan(
                new BackgroundColorSpan(container),
                window.start,
                window.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(
                new ForegroundColorSpan(onContainer),
                window.start,
                window.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }
}
