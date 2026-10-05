package com.pasich.mynotes.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * How a note reminder repeats: every {@code interval} {@link Unit units}, counted from an anchor.
 *
 * <p>The rule lives in the existing {@code reminderRepeat} text column and sync field, so it needs
 * no migration and older versions of the app keep reading what they understand:
 *
 * <ul>
 *   <li>{@code NONE}, {@code DAILY}, {@code WEEKLY} and {@code MONTHLY} are the legacy values. They
 *       carry no anchor; the reminder's own time is the anchor.
 *   <li>{@code EVERY:<n>:<UNIT>@<anchor epoch millis>} is everything else, for example {@code
 *       EVERY:3:HOURS@1767225600000}. An app that predates it reads it as a one-time reminder.
 * </ul>
 *
 * <p>Every occurrence is computed from the anchor rather than from the previous occurrence, so the
 * schedule never drifts and a month or year that is too short does not move later occurrences: a
 * monthly reminder anchored on 31 January rings on 28 or 29 February and again on 31 March, and a
 * yearly one anchored on 29 February rings on 28 February in other years. Hours are real elapsed
 * time; days and longer keep the same wall-clock time across daylight-saving changes.
 */
public final class RepeatRule {

    /** The smallest and largest repeat count the picker accepts. */
    public static final int MIN_INTERVAL = 1;

    public static final int MAX_INTERVAL = 999;

    /** A reminder that does not repeat. */
    public static final RepeatRule NONE = new RepeatRule(0, Unit.DAYS, null);

    private static final String PREFIX = "EVERY:";
    private static final long HOUR_MS = 3_600_000L;

    /** Repeat units, in picker order. */
    public enum Unit {
        HOURS(HOUR_MS),
        DAYS(25 * HOUR_MS),
        WEEKS(7 * 25 * HOUR_MS),
        MONTHS(31 * 25 * HOUR_MS),
        YEARS(366 * 25 * HOUR_MS);

        /**
         * An upper bound on the length of one unit, so that dividing elapsed time by it never
         * overshoots the number of whole periods that have passed (25-hour days cover a DST
         * change).
         */
        final long longestMillis;

        Unit(long longestMillis) {
            this.longestMillis = longestMillis;
        }
    }

    private final int interval;
    @NonNull private final Unit unit;
    @Nullable private final Long anchor;

    private RepeatRule(int interval, @NonNull Unit unit, @Nullable Long anchor) {
        this.interval = interval;
        this.unit = unit;
        this.anchor = anchor;
    }

    /**
     * A repeating rule. The interval is clamped to {@link #MIN_INTERVAL}..{@link #MAX_INTERVAL}.
     *
     * @param anchor the first occurrence, or null to use the reminder's own time
     */
    @NonNull
    public static RepeatRule every(int interval, @NonNull Unit unit, @Nullable Long anchor) {
        int clamped = Math.max(MIN_INTERVAL, Math.min(MAX_INTERVAL, interval));
        return new RepeatRule(clamped, unit, anchor);
    }

    /** Reads a stored value. Anything unrecognised, including null, is {@link #NONE}. */
    @NonNull
    public static RepeatRule parse(@Nullable String value) {
        if (value == null) return NONE;
        switch (value) {
            case "DAILY":
                return new RepeatRule(1, Unit.DAYS, null);
            case "WEEKLY":
                return new RepeatRule(1, Unit.WEEKS, null);
            case "MONTHLY":
                return new RepeatRule(1, Unit.MONTHS, null);
            default:
                break;
        }
        if (!value.startsWith(PREFIX)) return NONE;
        String body = value.substring(PREFIX.length());
        Long anchor = null;
        int at = body.indexOf('@');
        if (at >= 0) {
            try {
                anchor = Long.parseLong(body.substring(at + 1));
            } catch (NumberFormatException e) {
                return NONE;
            }
            body = body.substring(0, at);
        }
        int colon = body.indexOf(':');
        if (colon <= 0) return NONE;
        int interval;
        Unit unit;
        try {
            interval = Integer.parseInt(body.substring(0, colon));
            unit = Unit.valueOf(body.substring(colon + 1));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
        if (interval < MIN_INTERVAL || interval > MAX_INTERVAL) return NONE;
        return new RepeatRule(interval, unit, anchor);
    }

    /**
     * The stored form. A rule an older app version can express without an anchor is written as its
     * legacy value, so a reminder set to repeat daily, weekly or monthly keeps working there.
     * Monthly needs the anchor only when it falls after the 28th; without it a reminder that landed
     * on a short month's last day would stay on that day for good.
     */
    @NonNull
    public String serialize() {
        return serialize(ZoneId.systemDefault());
    }

    @NonNull
    String serialize(@NonNull ZoneId zone) {
        if (!isRepeating()) return "NONE";
        if (interval == 1) {
            if (unit == Unit.DAYS) return "DAILY";
            if (unit == Unit.WEEKS) return "WEEKLY";
            if (unit == Unit.MONTHS && (anchor == null || dayOfMonth(anchor, zone) <= 28))
                return "MONTHLY";
        }
        String rule = PREFIX + interval + ":" + unit.name();
        return anchor == null ? rule : rule + "@" + anchor;
    }

    public boolean isRepeating() {
        return interval > 0;
    }

    public int getInterval() {
        return interval;
    }

    @NonNull
    public Unit getUnit() {
        return unit;
    }

    @Nullable
    public Long getAnchor() {
        return anchor;
    }

    /** This rule counted from {@code newAnchor}. */
    @NonNull
    public RepeatRule withAnchor(long newAnchor) {
        if (!isRepeating()) return this;
        return new RepeatRule(interval, unit, newAnchor);
    }

    /** The anchor occurrences are counted from: the stored one, else {@code reminderTime}. */
    public long anchorOr(long reminderTime) {
        return anchor != null ? anchor : reminderTime;
    }

    /** The first occurrence strictly after {@code after}, in the device's time zone. */
    public long next(long anchor, long after) {
        return next(anchor, after, ZoneId.systemDefault());
    }

    /**
     * The first occurrence strictly after {@code after}, counting {@code anchor} as occurrence
     * zero. However many periods were missed, this is one step: catching up after the phone was off
     * for a month gives the next future occurrence, not a burst of the missed ones.
     *
     * @return {@code anchor} itself when it is still ahead, or -1 when the rule does not repeat
     */
    public long next(long anchor, long after, @NonNull ZoneId zone) {
        if (!isRepeating()) return -1L;
        if (anchor > after) return anchor;
        long periodLongest = unit.longestMillis * interval;
        // Never more than the true number of elapsed periods, so the loop only walks forward.
        long k = Math.max(0L, (after - anchor) / periodLongest);
        long candidate = occurrence(anchor, k, zone);
        while (candidate <= after) {
            k++;
            candidate = occurrence(anchor, k, zone);
        }
        return candidate;
    }

    /** Occurrence number {@code k}, counted from the anchor rather than from occurrence k-1. */
    long occurrence(long anchor, long k, @NonNull ZoneId zone) {
        long steps = k * interval;
        if (unit == Unit.HOURS) return anchor + steps * HOUR_MS;
        ZonedDateTime start = Instant.ofEpochMilli(anchor).atZone(zone);
        ZonedDateTime at =
                switch (unit) {
                    case DAYS -> start.plusDays(steps);
                    case WEEKS -> start.plusWeeks(steps);
                    case MONTHS -> start.plusMonths(steps);
                    default -> start.plusYears(steps);
                };
        return at.toInstant().toEpochMilli();
    }

    private static int dayOfMonth(long epochMillis, @NonNull ZoneId zone) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone).getDayOfMonth();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RepeatRule)) return false;
        RepeatRule other = (RepeatRule) o;
        return interval == other.interval
                && unit == other.unit
                && Objects.equals(anchor, other.anchor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(interval, unit, anchor);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "RepeatRule(%s)", serialize(ZoneId.of("UTC")));
    }
}
