package com.pasich.mynotes.data.database.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

/**
 * Local-to-sync identity mapping for records included in a sync snapshot.
 *
 * <p>The app's existing integer primary keys stay local. {@link #stableId} is immutable and is the
 * only ID that may be sent to another device. A row is retained after a local record is deleted so
 * {@link #deletedAt} can later be emitted as a tombstone.
 */
@Entity(
        tableName = "sync_metadata",
        primaryKeys = {"recordType", "localId"},
        indices = {
            @Index(
                    value = {"recordType", "stableId"},
                    unique = true),
            @Index(value = {"updatedAt"}),
            @Index(value = {"deletedAt"})
        })
public class SyncMetadataEntity {

    @NonNull public String recordType;

    public long localId;

    @NonNull public String stableId;

    public long updatedAt;

    public Long deletedAt;

    /**
     * Canonical hash of the version this device last published or applied for the record — the
     * version the remote is known to hold. A merge that finds the remote still equal to it knows
     * that only this device has moved and publishes without asking; without it every ordinary local
     * edit was offered to the user as a conflict against the text they had just replaced. Null
     * until the record has been through a sync on this build.
     */
    public String syncedVersionId;

    public SyncMetadataEntity(
            @NonNull String recordType,
            long localId,
            @NonNull String stableId,
            long updatedAt,
            Long deletedAt) {
        this.recordType = recordType;
        this.localId = localId;
        this.stableId = stableId;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }
}
