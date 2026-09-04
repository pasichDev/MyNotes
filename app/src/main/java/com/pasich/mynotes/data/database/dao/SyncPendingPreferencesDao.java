package com.pasich.mynotes.data.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.pasich.mynotes.data.database.entities.SyncPendingPreferencesEntity;

@Dao
public interface SyncPendingPreferencesDao {

    @Query("SELECT * FROM sync_pending_preferences WHERE id = 1 LIMIT 1")
    SyncPendingPreferencesEntity get();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SyncPendingPreferencesEntity pending);

    @Query("DELETE FROM sync_pending_preferences WHERE id = 1")
    void clear();
}
