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

    /**
     * Both sides of every settled conflict, most recently settled first; neither version may be
     * offered again.
     *
     * <p>The order matters: the bundle carries only a bounded number of these, and the plain UNION
     * this used to be returned them in whatever order SQLite's de-duplication left them, so the cap
     * dropped arbitrary — often the newest — settlements and offered them again.
     */
    @Query(
            "SELECT versionId FROM ("
                    + "SELECT winnerVersionId AS versionId, resolvedAt, id FROM sync_conflicts"
                    + " WHERE resolved = 1 AND winnerVersionId != ''"
                    + " UNION ALL SELECT loserVersionId AS versionId, resolvedAt, id"
                    + " FROM sync_conflicts WHERE resolved = 1 AND loserVersionId != '')"
                    + " GROUP BY versionId ORDER BY MAX(resolvedAt) DESC, MAX(id) DESC")
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

    /**
     * Deletes every settled conflict but the {@code keep} most recently settled ones.
     *
     * <p>Settled rows only exist to stop a version being offered again, and only the newest ones
     * are ever published; without this they accumulated for the life of the install.
     */
    @Query(
            "DELETE FROM sync_conflicts WHERE resolved = 1 AND id NOT IN ("
                    + "SELECT id FROM sync_conflicts WHERE resolved = 1"
                    + " ORDER BY resolvedAt DESC, id DESC LIMIT :keep)")
    int deleteResolvedBeyond(int keep);

    @Query(
            "UPDATE sync_conflicts SET resolution = :resolution, resolved = 1, resolvedAt = :resolvedAt WHERE id = :id")
    void markResolved(long id, String resolution, long resolvedAt);
}
