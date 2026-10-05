package com.pasich.mynotes.ui.history;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import com.google.android.material.color.MaterialColors;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.history.NoteVersionReason;
import com.pasich.mynotes.ui.sync.SyncConflictPresentation;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * "Just now, 14:05", "5 minutes ago, 14:05", "Yesterday, 14:05" or a date, in the user's
     * locale. Under a minute the system's wording says "0 minutes ago".
     */
    @NonNull
    public static CharSequence when(@NonNull Context context, long time) {
        if (isJustNow(System.currentTimeMillis(), time)) {
            return context.getString(
                    R.string.version_time_just_now,
                    android.text.format.DateFormat.getTimeFormat(context).format(time));
        }
        return DateUtils.getRelativeDateTimeString(
                context,
                time,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.WEEK_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_MONTH);
    }

    /** Whether a version kept at {@code time} is less than a minute old at {@code now}. */
    @VisibleForTesting
    static boolean isJustNow(long now, long time) {
        return Math.abs(now - time) < DateUtils.MINUTE_IN_MILLIS;
    }

    /**
     * Widens a highlighted range so it never starts or ends inside a word or a number: a change
     * from "1200" to "1500" marks "1200" and "1500", not "2" and "5". An empty range inside a word
     * (letters only added on the other side) marks that word.
     *
     * @return {@code {start, end}}.
     */
    @NonNull
    @VisibleForTesting
    static int[] wholeWords(@NonNull CharSequence text, int start, int end) {
        int length = text.length();
        int from = Math.max(0, Math.min(start, length));
        int to = Math.max(from, Math.min(end, length));
        while (from > 0
                && from < length
                && isWordChar(text.charAt(from - 1))
                && isWordChar(text.charAt(from))) {
            from--;
        }
        while (to > 0
                && to < length
                && isWordChar(text.charAt(to - 1))
                && isWordChar(text.charAt(to))) {
            to++;
        }
        return new int[] {from, to};
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }

    /** Most line pairs compared one by one; longer notes are compared as one block. */
    @VisibleForTesting static final int MAX_LINE_PAIRS = 250_000;

    /**
     * The parts of {@code text} that differ from {@code other}, as {@code {start, end}} ranges in
     * {@code text}, each widened to whole words.
     *
     * <p>The lines of both texts are matched first (longest common subsequence), so equal lines
     * stay unmarked however far apart the changes are; inside each run of changed lines only the
     * part between the common start and end is marked. Texts with too many lines to match fall back
     * to a single range from the first to the last difference.
     */
    @NonNull
    @VisibleForTesting
    static List<int[]> changedRanges(@NonNull String text, @NonNull String other) {
        List<int[]> ranges = new ArrayList<>();
        String[] a = text.split("\n", -1);
        String[] b = other.split("\n", -1);
        int n = a.length;
        int m = b.length;
        if ((long) n * m > MAX_LINE_PAIRS) {
            addChange(ranges, text, 0, text.length(), other);
            return ranges;
        }
        int[] starts = new int[n + 1];
        for (int i = 0; i < n; i++) starts[i + 1] = starts[i] + a[i].length() + 1;
        // common[i][j]: longest common run of lines of a[i..] and b[j..].
        int[][] common = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                common[i][j] =
                        a[i].equals(b[j])
                                ? common[i + 1][j + 1] + 1
                                : Math.max(common[i + 1][j], common[i][j + 1]);
            }
        }
        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && a[i].equals(b[j])) {
                i++;
                j++;
                continue;
            }
            int fromA = i;
            int fromB = j;
            while ((i < n || j < m) && !(i < n && j < m && a[i].equals(b[j]))) {
                if (j < m && (i == n || common[i][j + 1] >= common[i + 1][j])) {
                    j++;
                } else {
                    i++;
                }
            }
            if (i > fromA) {
                int start = starts[fromA];
                int end = Math.min(text.length(), starts[i] - 1);
                addChange(ranges, text, start, end, String.join("\n", sub(b, fromB, j)));
            }
        }
        return ranges;
    }

    private static String[] sub(String[] lines, int from, int to) {
        String[] out = new String[to - from];
        System.arraycopy(lines, from, out, 0, to - from);
        return out;
    }

    /** Marks what differs between {@code text[start, end)} and {@code replaced}. */
    private static void addChange(
            List<int[]> ranges, String text, int start, int end, String replaced) {
        String part = text.substring(start, end);
        int[] diff = SyncConflictPresentation.differenceRange(part, replaced);
        int[] range = wholeWords(text, start + diff[0], start + diff[1]);
        if (range[1] > range[0]) ranges.add(range);
    }

    /**
     * {@code text} with the parts that differ from {@code other} marked in the theme's primary
     * colours. Longer than {@code limit}, it is cut to a window around its first change.
     */
    @NonNull
    public static CharSequence highlighted(
            @NonNull Context context, @NonNull String text, @NonNull String other, int limit) {
        List<int[]> ranges = changedRanges(text, other);
        String shown = text;
        int shift = 0;
        if (text.length() > limit) {
            int[] first = ranges.isEmpty() ? new int[] {0, 0} : ranges.get(0);
            SyncConflictPresentation.Window window =
                    SyncConflictPresentation.window(text, first[0], first[1], limit);
            shown = window.text;
            shift = window.start - first[0];
        }
        if (ranges.isEmpty()) return shown;
        SpannableString marked = new SpannableString(shown);
        int container =
                MaterialColors.getColor(
                        context, com.google.android.material.R.attr.colorPrimaryContainer, 0);
        int onContainer =
                MaterialColors.getColor(
                        context, com.google.android.material.R.attr.colorOnPrimaryContainer, 0);
        for (int[] range : ranges) {
            int from = Math.max(0, range[0] + shift);
            int to = Math.min(shown.length(), range[1] + shift);
            if (to <= from) continue;
            marked.setSpan(
                    new BackgroundColorSpan(container),
                    from,
                    to,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            marked.setSpan(
                    new ForegroundColorSpan(onContainer),
                    from,
                    to,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return marked;
    }
}
