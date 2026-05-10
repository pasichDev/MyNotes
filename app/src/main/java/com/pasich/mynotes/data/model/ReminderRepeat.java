package com.pasich.mynotes.data.model;

public enum ReminderRepeat {
    NONE, DAILY, WEEKLY, MONTHLY;

    public static ReminderRepeat from(String value) {
        if (value == null) return NONE;
        try { return valueOf(value); } catch (Exception e) { return NONE; }
    }
}
