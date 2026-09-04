package com.pasich.mynotes.data.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.pasich.mynotes.data.database.entities.SyncConflictEntity;
import java.util.List;

@Dao
public interface SyncConflictDao {

    /** Exact repeated observations are harmless; distinct version pairs must coexist. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertIgnoringDuplicates(List<SyncConflictEntity> conflicts);

    @Query("DELETE FROM sync_conflicts")
    void clearAll();

    @Query("SELECT * FROM sync_conflicts ORDER BY resolved ASC, createdAt DESC, id DESC")
    List<SyncConflictEntity> getAll();

    @Query("SELECT * FROM sync_conflicts WHERE resolved = 0 ORDER BY createdAt DESC, id DESC")
    List<SyncConflictEntity> getUnresolved();

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE resolved = 0")
    int getUnresolvedCount();

    /** Both sides of every settled conflict; neither version may be offered again. */
    @Query(
            "SELECT winnerVersionId FROM sync_conflicts WHERE resolved = 1 AND winnerVersionId != ''"
                    + " UNION SELECT loserVersionId FROM sync_conflicts WHERE resolved = 1 AND"
                    + " loserVersionId != ''")
    List<String> getResolvedVersionIds();

    @Query("SELECT * FROM sync_conflicts WHERE id = :id LIMIT 1")
    SyncConflictEntity getById(long id);

    @Query("DELETE FROM sync_conflicts WHERE id = :id")
    void deleteById(long id);

    /**
     * Retires every open conflict for a record whose winner is no longer the version being applied.
     *
     * <p>Such a row offers, pre-selected, a version the record has since moved past; applying it
     * reverted the newer edit. The loser it carried is republished with the bundle and comes back
     * against the current version.
     */
    @Query(
            "DELETE FROM sync_conflicts WHERE resolved = 0 AND recordType = :recordType "
                    + "AND stableId = :stableId AND winnerVersionId != :winnerVersionId")
    void deleteSupersededUnresolved(String recordType, String stableId, String winnerVersionId);

    @Query(
            "UPDATE sync_conflicts SET resolution = :resolution, resolved = 1, resolvedAt = :resolvedAt WHERE id = :id")
    void markResolved(long id, String resolution, long resolvedAt);
}
