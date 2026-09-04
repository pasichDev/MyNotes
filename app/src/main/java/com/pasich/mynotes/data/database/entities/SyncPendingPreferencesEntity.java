package com.pasich.mynotes.data.database.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Room journal for a preference adapter mutation that must follow a snapshot transaction. */
@Entity(tableName = "sync_pending_preferences")
public final class SyncPendingPreferencesEntity {

    @PrimaryKey public int id;
    @NonNull public String payloadJson;

    public SyncPendingPreferencesEntity(int id, @NonNull String payloadJson) {
        this.id = id;
        this.payloadJson = payloadJson;
    }
}
