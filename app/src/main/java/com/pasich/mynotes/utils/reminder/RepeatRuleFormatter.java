package com.pasich.mynotes.utils.reminder;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.RepeatRule;

/** Words for a repeat rule, the same everywhere a reminder shows how it repeats. */
public final class RepeatRuleFormatter {

    private RepeatRuleFormatter() {}

    /** "Daily", "Every hour", "Every 3 hours"; null for a reminder that does not repeat. */
    @Nullable
    public static String summary(@NonNull Context ctx, @NonNull RepeatRule rule) {
        if (!rule.isRepeating()) return null;
        int n = rule.getInterval();
        if (n == 1) {
            return ctx.getString(
                    switch (rule.getUnit()) {
                        case HOURS -> R.string.reminder_repeat_hourly;
                        case DAYS -> R.string.reminder_repeat_daily;
                        case WEEKS -> R.string.reminder_repeat_weekly;
                        case MONTHS -> R.string.reminder_repeat_monthly;
                        case YEARS -> R.string.reminder_repeat_yearly;
                    });
        }
        return ctx.getResources().getQuantityString(everyPlural(rule.getUnit()), n, n);
    }

    /** The summary of a stored repeat value, or null when it does not repeat. */
    @Nullable
    public static String summary(@NonNull Context ctx, @Nullable String storedRepeat) {
        return summary(ctx, RepeatRule.parse(storedRepeat));
    }

    /** "3 hours", for the unit choices in the custom repeat dialog. */
    @NonNull
    public static String unitCount(@NonNull Context ctx, @NonNull RepeatRule.Unit unit, int n) {
        @PluralsRes
        int res =
                switch (unit) {
                    case HOURS -> R.plurals.reminder_unit_hours;
                    case DAYS -> R.plurals.reminder_unit_days;
                    case WEEKS -> R.plurals.reminder_unit_weeks;
                    case MONTHS -> R.plurals.reminder_unit_months;
                    case YEARS -> R.plurals.reminder_unit_years;
                };
        return ctx.getResources().getQuantityString(res, n, n);
    }

    @PluralsRes
    private static int everyPlural(@NonNull RepeatRule.Unit unit) {
        return switch (unit) {
            case HOURS -> R.plurals.reminder_repeat_every_hours;
            case DAYS -> R.plurals.reminder_repeat_every_days;
            case WEEKS -> R.plurals.reminder_repeat_every_weeks;
            case MONTHS -> R.plurals.reminder_repeat_every_months;
            case YEARS -> R.plurals.reminder_repeat_every_years;
        };
    }
}
