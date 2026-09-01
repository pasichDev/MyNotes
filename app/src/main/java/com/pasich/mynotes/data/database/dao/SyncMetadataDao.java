package com.pasich.mynotes.data.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.pasich.mynotes.data.database.entities.SyncMetadataEntity;
import java.util.List;

/** DAO for the durable sync identity, modification time, and deletion tombstone metadata. */
@Dao
public interface SyncMetadataDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIfAbsent(SyncMetadataEntity metadata);

    @Query(
            "UPDATE sync_metadata SET updatedAt = :updatedAt, deletedAt = :deletedAt WHERE recordType = :recordType AND localId = :localId")
    void setVersion(String recordType, long localId, long updatedAt, Long deletedAt);

    @Query(
            "SELECT * FROM sync_metadata "
                    + "WHERE recordType = :recordType AND localId = :localId LIMIT 1")
    SyncMetadataEntity get(String recordType, long localId);

    @Query(
            "SELECT EXISTS(SELECT 1 FROM sync_metadata "
                    + "WHERE recordType = :recordType AND localId = :localId)")
    boolean exists(String recordType, long localId);

    @Query(
            "SELECT * FROM sync_metadata WHERE recordType = :recordType AND stableId = :stableId LIMIT 1")
    SyncMetadataEntity getByStableId(String recordType, String stableId);

    @Query("SELECT * FROM sync_metadata WHERE deletedAt IS NULL ORDER BY recordType, stableId")
    List<SyncMetadataEntity> getLiveRecords();

    @Query("SELECT * FROM sync_metadata WHERE deletedAt IS NOT NULL ORDER BY recordType, stableId")
    List<SyncMetadataEntity> getTombstones();

    @Query("SELECT * FROM sync_metadata ORDER BY recordType, stableId")
    List<SyncMetadataEntity> getAll();

    @Query("SELECT * FROM sync_metadata WHERE updatedAt > :timestamp ORDER BY updatedAt, stableId")
    List<SyncMetadataEntity> getChangedSince(long timestamp);

    /** Marks a local record as changed and clears a previous deletion marker. */
    @Query(
            "UPDATE sync_metadata SET "
                    + "updatedAt = CASE WHEN updatedAt >= :nowMillis THEN updatedAt + 1 ELSE :nowMillis END, "
                    + "deletedAt = NULL "
                    + "WHERE recordType = :recordType AND localId = :localId")
    void touch(String recordType, long localId, long nowMillis);

    /** Retains the metadata row and records a monotonic tombstone timestamp. */
    @Query(
            "UPDATE sync_metadata SET "
                    + "updatedAt = CASE WHEN updatedAt >= :nowMillis THEN updatedAt + 1 ELSE :nowMillis END, "
                    + "deletedAt = CASE WHEN updatedAt >= :nowMillis THEN updatedAt + 1 ELSE :nowMillis END "
                    + "WHERE recordType = :recordType AND localId = :localId")
    void markDeleted(String recordType, long localId, long nowMillis);
}
