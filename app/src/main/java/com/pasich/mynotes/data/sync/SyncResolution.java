package com.pasich.mynotes.data.sync;

public enum SyncResolution {
    PENDING,
    KEEP_LOCAL,
    KEEP_DRIVE;

    public static SyncResolution fromStoredValue(String value) {
        if (value == null || value.trim().isEmpty()) return PENDING;
        try {
            return SyncResolution.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return PENDING;
        }
    }
}
