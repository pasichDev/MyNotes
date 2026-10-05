package com.pasich.mynotes.utils.reminder;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.pasich.mynotes.data.model.RepeatRule;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * What the reminder sheet holds before it is saved: the chosen time, the repeat, the repeated
 * notification and a date picked while its time is still being chosen. Kept in the sheet's saved
 * state so a rotation keeps all of it.
 */
public final class ReminderDraft {

    private static final String KEY_TIME = "draft.time";
    private static final String KEY_REPEAT_CHIP = "draft.repeatChip";
    private static final String KEY_CUSTOM_RULE = "draft.customRule";
    private static final String KEY_INTERVAL = "draft.intervalMinutes";
    private static final String KEY_PICKED_DATE = "draft.pickedDate";

    /** The chosen time, or null while none is chosen. */
    @Nullable public Long time;

    /** The checked repeat chip, 0 for the layout's default. */
    public int repeatChipId;

    /** The rule behind the Custom chip, or null while it has none. */
    @Nullable public RepeatRule customRule;

    /** Minutes between repeated notifications, 0 for none. */
    public int intervalMinutes;

    /** A day picked in the date picker (UTC midnight) whose time is being chosen, or null. */
    @Nullable public Long pickedDateUtc;

    public void save(@NonNull Bundle out) {
        if (time != null) out.putLong(KEY_TIME, time);
        out.putInt(KEY_REPEAT_CHIP, repeatChipId);
        if (customRule != null) out.putString(KEY_CUSTOM_RULE, customRule.serialize());
        out.putInt(KEY_INTERVAL, intervalMinutes);
        if (pickedDateUtc != null) out.putLong(KEY_PICKED_DATE, pickedDateUtc);
    }

    /** The draft kept in {@code in}, or null when there is none (the sheet opens fresh). */
    @Nullable
    public static ReminderDraft restore(@Nullable Bundle in) {
        if (in == null || !in.containsKey(KEY_REPEAT_CHIP)) return null;
        ReminderDraft draft = new ReminderDraft();
        if (in.containsKey(KEY_TIME)) draft.time = in.getLong(KEY_TIME);
        draft.repeatChipId = in.getInt(KEY_REPEAT_CHIP);
        String rule = in.getString(KEY_CUSTOM_RULE);
        if (rule != null) {
            RepeatRule parsed = RepeatRule.parse(rule);
            draft.customRule = parsed.isRepeating() ? parsed : null;
        }
        draft.intervalMinutes = in.getInt(KEY_INTERVAL);
        if (in.containsKey(KEY_PICKED_DATE)) draft.pickedDateUtc = in.getLong(KEY_PICKED_DATE);
        return draft;
    }

    /**
     * The moment for a day from the date picker, which gives UTC midnight of that day, at a time of
     * day in {@code zone}.
     */
    public static long at(long pickedDateUtc, int hour, int minute, @NonNull TimeZone zone) {
        Calendar day = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        day.setTimeInMillis(pickedDateUtc);
        Calendar local = Calendar.getInstance(zone);
        local.clear();
        local.set(
                day.get(Calendar.YEAR),
                day.get(Calendar.MONTH),
                day.get(Calendar.DAY_OF_MONTH),
                hour,
                minute,
                0);
        return local.getTimeInMillis();
    }
}
