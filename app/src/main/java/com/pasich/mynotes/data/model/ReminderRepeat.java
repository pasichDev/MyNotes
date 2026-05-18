package com.pasich.mynotes.data.model;

/** Repeat interval options for note reminders. */
public enum ReminderRepeat {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY;

    /** Parses a string to a ReminderRepeat value, defaulting to NONE. */
    public static ReminderRepeat from(String value) {
        if (value == null) return NONE;
        try {
            return valueOf(value);
        } catch (Exception e) {
            return NONE;
        }
    }
}
