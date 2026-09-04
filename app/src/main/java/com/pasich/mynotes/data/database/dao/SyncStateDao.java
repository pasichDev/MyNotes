package com.pasich.mynotes.data.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.pasich.mynotes.data.database.entities.SyncStateEntity;

/** DAO for the singleton sync status row. */
@Dao
public interface SyncStateDao {

    @Query("SELECT * FROM sync_state WHERE id = 1 LIMIT 1")
    SyncStateEntity get();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SyncStateEntity state);

    /** Forgets the stored status, so a freshly connected account starts from idle. */
    @Query("DELETE FROM sync_state")
    void clear();
}
