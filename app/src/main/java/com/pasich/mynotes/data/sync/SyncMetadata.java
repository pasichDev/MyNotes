package com.pasich.mynotes.data.sync;

import java.util.Locale;
import java.util.UUID;

/** Shared sync identity and timestamp rules for locally persisted records. */
public final class SyncMetadata {

    public static final String RECORD_TYPE_NOTE = "note";
    public static final String RECORD_TYPE_CATEGORY = "category";
    public static final String RECORD_TYPE_TASK = "task";
    public static final String RECORD_TYPE_TAG = "tag";
    public static final String RECORD_TYPE_PREFERENCES = "preferences";

    private SyncMetadata() {}

    /** Returns a canonical, lowercase UUID suitable for use as a cross-device record ID. */
    public static String newStableId() {
        return UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
    }

    /** Returns true only for the record types defined by sync schema version 1. */
    public static boolean isSupportedRecordType(String recordType) {
        return RECORD_TYPE_NOTE.equals(recordType)
                || RECORD_TYPE_TASK.equals(recordType)
                || RECORD_TYPE_TAG.equals(recordType)
                || RECORD_TYPE_CATEGORY.equals(recordType)
                || RECORD_TYPE_PREFERENCES.equals(recordType);
    }

    /**
     * Produces a monotonic mutation timestamp for one logical record.
     *
     * <p>A device clock can move backwards. In that case the next timestamp advances the prior
     * value by one millisecond, so merge ordering remains deterministic.
     */
    public static long nextUpdatedAt(long previousUpdatedAt, long nowMillis) {
        return nowMillis > previousUpdatedAt ? nowMillis : previousUpdatedAt + 1L;
    }
}
