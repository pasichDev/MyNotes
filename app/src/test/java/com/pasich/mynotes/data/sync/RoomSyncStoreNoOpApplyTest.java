package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.database.Cursor;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.google.gson.JsonObject;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.entities.SyncMetadataEntity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import io.reactivex.observers.BaseTestConsumer;
import io.reactivex.subscribers.TestSubscriber;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * A sync that changes nothing must write nothing.
 *
 * <p>Every sync applies the whole merged snapshot. Re-applying each known record with a REPLACE
 * insert or an update invalidated the notes, tags and tasks queries on every sync, so the main list
 * was re-emitted and re-animated even when no other device had changed anything. The writes are
 * counted with SQLite triggers on the same tables Room's invalidation tracker watches.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RoomSyncStoreNoOpApplyTest {

    private static final String NOTE_ID = "11111111-1111-4111-8111-111111111111";
    private static final String EMPTY_LIST_NOTE_ID = "22222222-2222-4222-8222-222222222222";
    private static final String TAG_ID = "33333333-3333-4333-8333-333333333333";
    private static final String CATEGORY_ID = "44444444-4444-4444-8444-444444444444";
    private static final String TASK_ID = "55555555-5555-4555-8555-555555555555";
    private static final String[] WATCHED =
            new String[] {"notes", "tags", "tasks", "task_categories", "sync_metadata"};

    private AppDatabase db;
    private RoomSyncStore store;
    private int noteId;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        db =
                Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                        .allowMainThreadQueries()
                        .build();
        store = new RoomSyncStore(context, db, mock(PreferenceHelper.class));

        noteId = seedNote("Shopping", "Milk", null, NOTE_ID);
        // The editor stores "no attachments" as "[]"; a received version carries none at all.
        seedNote("Plain", "Body", "[]", EMPTY_LIST_NOTE_ID);
        int tagId = (int) db.tagsDao().addTag(new Tag().create("Work"));
        seedMetadata(SyncMetadata.RECORD_TYPE_TAG, tagId, TAG_ID);
        int categoryId =
                (int) db.taskCategoryDao().insertCategory(new TaskCategory("Home", "#123456"));
        seedMetadata(SyncMetadata.RECORD_TYPE_CATEGORY, categoryId, CATEGORY_ID);
        int taskId = (int) db.taskDao().insertTask(new Task("Call", categoryId));
        seedMetadata(SyncMetadata.RECORD_TYPE_TASK, taskId, TASK_ID);
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void applyingWhatThisDeviceAlreadyHoldsWritesNoRows() throws Exception {
        SyncSnapshot first = store.readSnapshot();
        countWrites();

        store.applySnapshot(first, Collections.emptyList());

        // The first sync only records the versions the remote now holds.
        Map<String, Integer> writes = writesByTable();
        assertThat(writes.keySet()).containsExactly("sync_metadata");

        SyncSnapshot second = store.readSnapshot();
        clearWrites();
        store.applySnapshot(second, Collections.emptyList());

        // Every later sync of an unchanged library touches nothing at all.
        assertThat(writesByTable()).isEmpty();
        assertThat(db.noteDao().getNoteSync(noteId).getTitle()).isEqualTo("Shopping");
        SyncMetadataEntity metadata =
                db.syncMetadataDao().get(SyncMetadata.RECORD_TYPE_NOTE, noteId);
        assertThat(metadata.syncedVersionId)
                .isEqualTo(second.find(SyncRecord.Type.NOTE, NOTE_ID).getCanonicalPayloadHash());
    }

    @Test
    public void aNoOpApplyDoesNotReEmitTheNotesList() throws Exception {
        store.applySnapshot(store.readSnapshot(), Collections.emptyList());
        SyncSnapshot unchanged = store.readSnapshot();
        TestSubscriber<List<Note>> notes = db.noteDao().getNotesAll().test();
        notes.awaitCount(1, BaseTestConsumer.TestWaitStrategy.SLEEP_10MS, 5_000L);
        assertThat(notes.valueCount()).isEqualTo(1);

        store.applySnapshot(unchanged, Collections.emptyList());
        notes.awaitCount(2, BaseTestConsumer.TestWaitStrategy.SLEEP_10MS, 1_000L);
        assertThat(notes.valueCount()).isEqualTo(1);

        // The control: a version that does change the note is still delivered to the list.
        store.applySnapshot(
                new SyncSnapshot(Collections.singletonList(renamed(unchanged, "Groceries"))),
                Collections.emptyList());
        notes.awaitCount(2, BaseTestConsumer.TestWaitStrategy.SLEEP_10MS, 5_000L);
        assertThat(notes.valueCount()).isEqualTo(2);
        notes.dispose();
    }

    @Test
    public void aChangedRecordIsStillAppliedAndOnlyItIsWritten() throws Exception {
        store.applySnapshot(store.readSnapshot(), Collections.emptyList());
        SyncSnapshot built = store.readSnapshot();
        SyncRecord edited = renamed(built, "Groceries");
        List<SyncRecord> merged = new ArrayList<>();
        for (SyncRecord record : built.getRecords()) {
            merged.add(record.getId().equals(NOTE_ID) ? edited : record);
        }
        countWrites();

        store.applySnapshot(new SyncSnapshot(merged), Collections.emptyList());

        assertThat(db.noteDao().getNoteSync(noteId).getTitle()).isEqualTo("Groceries");
        SyncMetadataEntity metadata =
                db.syncMetadataDao().get(SyncMetadata.RECORD_TYPE_NOTE, noteId);
        assertThat(metadata.updatedAt).isEqualTo(edited.getUpdatedAt().toEpochMilli());
        assertThat(metadata.syncedVersionId).isEqualTo(edited.getCanonicalPayloadHash());
        assertThat(writesByTable().keySet()).containsExactly("notes", "sync_metadata");
    }

    @Test
    public void aTombstoneIsStillApplied() throws Exception {
        store.applySnapshot(store.readSnapshot(), Collections.emptyList());
        SyncRecord built = store.readSnapshot().find(SyncRecord.Type.NOTE, NOTE_ID);
        Instant deletedAt = built.getUpdatedAt().plusSeconds(1);
        SyncRecord tombstone =
                SyncRecord.tombstone(SyncRecord.Type.NOTE, NOTE_ID, deletedAt, deletedAt);

        store.applySnapshot(
                new SyncSnapshot(Collections.singletonList(tombstone)), Collections.emptyList());

        assertThat(db.noteDao().getNoteSync(noteId)).isNull();
        SyncMetadataEntity metadata =
                db.syncMetadataDao().get(SyncMetadata.RECORD_TYPE_NOTE, noteId);
        assertThat(metadata.deletedAt).isEqualTo(deletedAt.toEpochMilli());
        assertThat(metadata.syncedVersionId).isEqualTo(tombstone.getCanonicalPayloadHash());

        // Applied again, the same deletion has nothing left to do.
        countWrites();
        store.applySnapshot(
                new SyncSnapshot(Collections.singletonList(tombstone)), Collections.emptyList());
        assertThat(writesByTable()).isEmpty();
    }

    @Test
    public void aChangedTaskIsStillAppliedWhileItsCategoryIsLeftAlone() throws Exception {
        store.applySnapshot(store.readSnapshot(), Collections.emptyList());
        SyncSnapshot built = store.readSnapshot();
        SyncRecord task = built.find(SyncRecord.Type.TASK, TASK_ID);
        JsonObject payload = task.getPayload().deepCopy();
        payload.addProperty("title", "Call back");
        List<SyncRecord> merged = new ArrayList<>();
        for (SyncRecord record : built.getRecords()) {
            merged.add(
                    record.getId().equals(TASK_ID)
                            ? SyncRecord.live(
                                    SyncRecord.Type.TASK,
                                    TASK_ID,
                                    task.getUpdatedAt().plusSeconds(1),
                                    payload)
                            : record);
        }
        countWrites();

        store.applySnapshot(new SyncSnapshot(merged), Collections.emptyList());

        long taskId =
                db.syncMetadataDao().getByStableId(SyncMetadata.RECORD_TYPE_TASK, TASK_ID).localId;
        Task stored = db.taskDao().getTaskSync((int) taskId);
        assertThat(stored.getTitle()).isEqualTo("Call back");
        long categoryId =
                db.syncMetadataDao()
                        .getByStableId(SyncMetadata.RECORD_TYPE_CATEGORY, CATEGORY_ID)
                        .localId;
        assertThat(stored.getCategoryId()).isEqualTo((int) categoryId);
        assertThat(writesByTable().keySet()).containsExactly("tasks", "sync_metadata");
    }

    // ---- helpers ----

    private SyncRecord renamed(SyncSnapshot snapshot, String title) {
        SyncRecord note = snapshot.find(SyncRecord.Type.NOTE, NOTE_ID);
        JsonObject payload = note.getPayload().deepCopy();
        payload.addProperty("b", title);
        return SyncRecord.live(
                SyncRecord.Type.NOTE, NOTE_ID, note.getUpdatedAt().plusSeconds(1), payload);
    }

    private int seedNote(String title, String value, String attachmentsJson, String stableId) {
        Note note = new Note().create(title, value, 1_000L, "");
        note.setAttachments(attachmentsJson);
        int id = db.noteDao().addNote(note).intValue();
        seedMetadata(SyncMetadata.RECORD_TYPE_NOTE, id, stableId);
        return id;
    }

    private void seedMetadata(String recordType, long localId, String stableId) {
        db.syncMetadataDao()
                .insertIfAbsent(
                        new SyncMetadataEntity(recordType, localId, stableId, 1_000L, null));
    }

    private SupportSQLiteDatabase sql() {
        return db.getOpenHelper().getWritableDatabase();
    }

    /** Installs triggers that log every row written to the watched tables from now on. */
    private void countWrites() {
        sql().execSQL("CREATE TEMP TABLE IF NOT EXISTS row_writes(tbl TEXT NOT NULL)");
        for (String table : WATCHED) {
            for (String operation : new String[] {"INSERT", "UPDATE", "DELETE"}) {
                sql().execSQL(
                                "CREATE TEMP TRIGGER IF NOT EXISTS count_"
                                        + table
                                        + "_"
                                        + operation
                                        + " AFTER "
                                        + operation
                                        + " ON "
                                        + table
                                        + " BEGIN INSERT INTO row_writes VALUES('"
                                        + table
                                        + "'); END");
            }
        }
        clearWrites();
    }

    private void clearWrites() {
        sql().execSQL("DELETE FROM row_writes");
    }

    private Map<String, Integer> writesByTable() {
        Map<String, Integer> writes = new HashMap<>();
        try (Cursor cursor = sql().query("SELECT tbl, COUNT(*) FROM row_writes GROUP BY tbl")) {
            while (cursor.moveToNext()) {
                writes.put(cursor.getString(0), cursor.getInt(1));
            }
        }
        return writes;
    }
}
