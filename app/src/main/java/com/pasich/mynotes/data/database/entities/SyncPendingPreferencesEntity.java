package com.pasich.mynotes.data.database.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room journal for a preference write that must follow a committed snapshot transaction.
 *
 * <p>SharedPreferences sits outside Room, so the two are bridged by writing this row inside the
 * transaction and clearing it only once the adapter reports a durable commit. The two digests make
 * the replay decidable rather than blind: {@code baselineHash} is what the live preferences looked
 * like when the journal was written and {@code targetHash} is what they should look like
 * afterwards, so recovery can tell "already applied" from "still pending" from "the user has since
 * changed these settings themselves".
 */
@Entity(tableName = "sync_pending_preferences")
public final class SyncPendingPreferencesEntity {

    @PrimaryKey public int id;

    @NonNull public String payloadJson;

    /** Digest of the preferences this journal is meant to produce. */
    @NonNull public String targetHash;

    /** Digest of the live preferences at the moment the journal was written. */
    @NonNull public String baselineHash;

    /** {@code updatedAt} of the sync record the payload came from. */
    public long recordUpdatedAt;

    /** Set when the payload could not be read; retained for support, skipped by recovery. */
    public boolean quarantined;

    /** The conflict this write settles, or 0 when it comes from an ordinary snapshot apply. */
    public long conflictId;

    /** The resolution to record once the write is durable; empty when there is no conflict. */
    @NonNull public String conflictResolution;

    public SyncPendingPreferencesEntity(
            int id,
            @NonNull String payloadJson,
            @NonNull String targetHash,
            @NonNull String baselineHash,
            long recordUpdatedAt,
            boolean quarantined,
            long conflictId,
            @NonNull String conflictResolution) {
        this.id = id;
        this.payloadJson = payloadJson;
        this.targetHash = targetHash;
        this.baselineHash = baselineHash;
        this.recordUpdatedAt = recordUpdatedAt;
        this.quarantined = quarantined;
        this.conflictId = conflictId;
        this.conflictResolution = conflictResolution;
    }
}
