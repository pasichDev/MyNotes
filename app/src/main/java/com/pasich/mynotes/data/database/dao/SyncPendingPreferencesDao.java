package com.pasich.mynotes.data.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.pasich.mynotes.data.database.entities.SyncPendingPreferencesEntity;

@Dao
public interface SyncPendingPreferencesDao {

    /** The journal awaiting replay. Quarantined rows are deliberately invisible here. */
    @Query("SELECT * FROM sync_pending_preferences WHERE id = 1 AND quarantined = 0 LIMIT 1")
    SyncPendingPreferencesEntity get();

    /** Includes quarantined rows; for diagnostics and tests only. */
    @Query("SELECT * FROM sync_pending_preferences WHERE id = 1 LIMIT 1")
    SyncPendingPreferencesEntity getIncludingQuarantined();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SyncPendingPreferencesEntity pending);

    @Query("DELETE FROM sync_pending_preferences WHERE id = 1")
    void clear();

    /**
     * Sets a journal aside instead of deleting it.
     *
     * <p>An unreadable payload used to be thrown from {@code ensureSeeded}, which gates both
     * snapshot building and the status read, so one bad row disabled sync permanently with no way
     * to clear it. Quarantining keeps the bytes for support while letting sync run again.
     */
    @Query("UPDATE sync_pending_preferences SET quarantined = 1 WHERE id = 1")
    void quarantine();
}
