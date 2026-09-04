package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonObject;
import org.junit.Test;

public class SyncMetadataTest {

    @Test
    public void newStableId_isCanonicalLowercaseUuid() {
        String stableId = SyncMetadata.newStableId();

        assertThat(stableId)
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    public void nextUpdatedAt_advancesWhenClockMovesBackwards() {
        assertThat(SyncMetadata.nextUpdatedAt(100L, 101L)).isEqualTo(101L);
        assertThat(SyncMetadata.nextUpdatedAt(100L, 100L)).isEqualTo(101L);
        assertThat(SyncMetadata.nextUpdatedAt(100L, 99L)).isEqualTo(101L);
    }

    @Test
    public void stripDeviceLocalFields_removesRoomKeysAndAttachmentPaths() {
        JsonObject note = new JsonObject();
        note.addProperty("a", 5); // Note.id
        note.addProperty("b", "Shopping"); // Note.title
        note.addProperty("h", "[{\"url\":\"file://attachments/note_5/x.png\"}]");
        SyncMetadata.stripDeviceLocalFields(SyncMetadata.RECORD_TYPE_NOTE, note);
        assertThat(note.has("a")).isFalse();
        assertThat(note.has("h")).isFalse();
        assertThat(note.get("b").getAsString()).isEqualTo("Shopping");

        JsonObject task = new JsonObject();
        task.addProperty("id", 7);
        task.addProperty("categoryId", 3);
        task.addProperty("title", "Buy milk");
        SyncMetadata.stripDeviceLocalFields(SyncMetadata.RECORD_TYPE_TASK, task);
        assertThat(task.has("id")).isFalse();
        assertThat(task.has("categoryId")).isFalse();
        assertThat(task.get("title").getAsString()).isEqualTo("Buy milk");
    }

    @Test
    public void stripDeviceLocalFields_leavesPreferencesUntouched() {
        // PreferencesBackup is serialized with the same short Gson aliases as Note, so "a" is the
        // format count and "h" is a real setting. Stripping by key without checking the record
        // type would silently drop two of the user's settings from every sync.
        JsonObject preferences = new JsonObject();
        preferences.addProperty("a", 2);
        preferences.addProperty("h", true);

        SyncMetadata.stripDeviceLocalFields(SyncMetadata.RECORD_TYPE_PREFERENCES, preferences);

        assertThat(preferences.get("a").getAsInt()).isEqualTo(2);
        assertThat(preferences.get("h").getAsBoolean()).isTrue();
    }

    @Test
    public void supportedRecordTypes_matchSyncSchema() {
        assertThat(SyncMetadata.isSupportedRecordType(SyncMetadata.RECORD_TYPE_NOTE)).isTrue();
        assertThat(SyncMetadata.isSupportedRecordType(SyncMetadata.RECORD_TYPE_TASK)).isTrue();
        assertThat(SyncMetadata.isSupportedRecordType(SyncMetadata.RECORD_TYPE_TAG)).isTrue();
        assertThat(SyncMetadata.isSupportedRecordType("task_category")).isFalse();
    }
}
