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

    /**
     * Removes every payload field that only means something on the device that wrote it.
     *
     * <p>Room primary keys and attachment {@code file://} paths differ per device, so leaving them
     * in the payload makes two devices compute different canonical hashes for the same logical
     * record. Because {@code applySnapshot} copies the record's {@code updatedAt} verbatim, the
     * next merge sees equal timestamps, falls through to the hash tiebreaker, and reports a
     * conflict for every note, tag, task and category on every sync forever. It also keeps {@code
     * snapshotsMatch} permanently false, so each sync republishes a full bundle.
     *
     * <p>Identity travels in the record's stable ID and attachments travel in the bundle manifest,
     * so nothing here is needed on the wire. Applied to decoded remote records as well, so bundles
     * written by 2.6.48/2.6.49 normalize to the same shape instead of conflicting forever.
     */
    public static void stripDeviceLocalFields(
            String recordType, com.google.gson.JsonObject payload) {
        if (payload == null) {
            return;
        }
        if (RECORD_TYPE_NOTE.equals(recordType)) {
            payload.remove("a"); // Note.id
            payload.remove("h"); // Note.attachments: device-local file:// paths
        } else if (RECORD_TYPE_TAG.equals(recordType)) {
            payload.remove("a"); // Tag.id
        } else if (RECORD_TYPE_TASK.equals(recordType)) {
            payload.remove("id");
            payload.remove("categoryId"); // travels as categoryStableId
        } else if (RECORD_TYPE_CATEGORY.equals(recordType)) {
            payload.remove("id");
        }
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
