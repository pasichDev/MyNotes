package com.pasich.mynotes.data.model;

/**
 * The fixed repeat options older versions of the app stored, and still the stored form of a daily,
 * weekly or monthly reminder. {@link RepeatRule} reads these and everything newer.
 */
public enum ReminderRepeat {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY;

    /**
     * Parses a string to a ReminderRepeat value, defaulting to NONE. A custom rule such as {@code
     * EVERY:3:HOURS@…} is NONE here, which is how a version that predates it treats it.
     */
    public static ReminderRepeat from(String value) {
        if (value == null) return NONE;
        try {
            return valueOf(value);
        } catch (Exception e) {
            return NONE;
        }
    }

    /** The same repeat as a {@link RepeatRule}, anchored on the reminder's own time. */
    public RepeatRule toRule() {
        return RepeatRule.parse(name());
    }
}
