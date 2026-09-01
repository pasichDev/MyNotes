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
