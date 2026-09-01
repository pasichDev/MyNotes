package com.pasich.mynotes.data.database.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Durable singleton containing the last sync state. */
@Entity(tableName = "sync_state")
public final class SyncStateEntity {

    @PrimaryKey public int id;

    @NonNull public String status;
    @Nullable public String backendIdentifier;
    @Nullable public Long lastSuccessfulSyncAt;
    @Nullable public Long attemptStartedAt;
    @Nullable public String errorMessage;
    public int conflictCount;

    public SyncStateEntity(
            int id,
            @NonNull String status,
            @Nullable String backendIdentifier,
            @Nullable Long lastSuccessfulSyncAt,
            @Nullable Long attemptStartedAt,
            @Nullable String errorMessage,
            int conflictCount) {
        this.id = id;
        this.status = status;
        this.backendIdentifier = backendIdentifier;
        this.lastSuccessfulSyncAt = lastSuccessfulSyncAt;
        this.attemptStartedAt = attemptStartedAt;
        this.errorMessage = errorMessage;
        this.conflictCount = conflictCount;
    }
}
