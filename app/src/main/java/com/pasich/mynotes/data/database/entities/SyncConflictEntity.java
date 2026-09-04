package com.pasich.mynotes.data.database.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Persisted sync conflict report used for user-visible resolution after a sync completes. */
@Entity(
        tableName = "sync_conflicts",
        indices = {
            @Index(
                    value = {"recordType", "stableId", "versionPairHash"},
                    unique = true),
            @Index(value = {"resolved"}),
            @Index(value = {"createdAt"})
        })
public class SyncConflictEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull public String recordType;
    @NonNull public String stableId;

    /** Stable digest of the exact winner/loser version pair; never use the mutable row id. */
    @NonNull public String versionPairHash;

    /** Origin of the winning version: LOCAL, REMOTE. */
    @NonNull public String winnerSource;

    /** Origin of the losing version; both sides are REMOTE for a Drive-vs-Drive conflict. */
    @NonNull public String loserSource;

    /** Deterministic identity of the winning version, equal on every device. */
    @NonNull public String winnerVersionId;

    /** Deterministic identity of the losing version, equal on every device. */
    @NonNull public String loserVersionId;

    @NonNull public String winnerJson;
    @NonNull public String loserJson;
    public long winnerUpdatedAt;
    public long loserUpdatedAt;
    public boolean winnerTombstone;
    public boolean loserTombstone;
    @NonNull public String resolution;
    public boolean resolved;
    public long createdAt;
    public long resolvedAt;

    public SyncConflictEntity(
            @NonNull String recordType,
            @NonNull String stableId,
            @NonNull String versionPairHash,
            @NonNull String winnerSource,
            @NonNull String loserSource,
            @NonNull String winnerVersionId,
            @NonNull String loserVersionId,
            @NonNull String winnerJson,
            @NonNull String loserJson,
            long winnerUpdatedAt,
            long loserUpdatedAt,
            boolean winnerTombstone,
            boolean loserTombstone,
            @NonNull String resolution,
            boolean resolved,
            long createdAt,
            long resolvedAt) {
        this.recordType = recordType;
        this.stableId = stableId;
        this.versionPairHash = versionPairHash;
        this.winnerSource = winnerSource;
        this.loserSource = loserSource;
        this.winnerVersionId = winnerVersionId;
        this.loserVersionId = loserVersionId;
        this.winnerJson = winnerJson;
        this.loserJson = loserJson;
        this.winnerUpdatedAt = winnerUpdatedAt;
        this.loserUpdatedAt = loserUpdatedAt;
        this.winnerTombstone = winnerTombstone;
        this.loserTombstone = loserTombstone;
        this.resolution = resolution;
        this.resolved = resolved;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
    }
}
