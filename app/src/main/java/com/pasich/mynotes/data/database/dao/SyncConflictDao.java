package com.pasich.mynotes.data.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.pasich.mynotes.data.database.entities.SyncConflictEntity;
import java.util.List;

@Dao
public interface SyncConflictDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void replaceAll(List<SyncConflictEntity> conflicts);

    @Query("DELETE FROM sync_conflicts")
    void clearAll();

    @Query("SELECT * FROM sync_conflicts ORDER BY resolved ASC, createdAt DESC, id DESC")
    List<SyncConflictEntity> getAll();

    @Query("SELECT * FROM sync_conflicts WHERE resolved = 0 ORDER BY createdAt DESC, id DESC")
    List<SyncConflictEntity> getUnresolved();

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE resolved = 0")
    int getUnresolvedCount();

    @Query("SELECT * FROM sync_conflicts WHERE id = :id LIMIT 1")
    SyncConflictEntity getById(long id);

    @Query(
            "UPDATE sync_conflicts SET resolution = :resolution, resolved = 1, resolvedAt = :resolvedAt WHERE id = :id")
    void markResolved(long id, String resolution, long resolvedAt);
}
