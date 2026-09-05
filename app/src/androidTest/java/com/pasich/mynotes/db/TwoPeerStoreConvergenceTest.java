package com.pasich.mynotes.db;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import android.content.Context;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.entities.SyncConflictEntity;
import com.pasich.mynotes.data.database.entities.SyncMetadataEntity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import com.pasich.mynotes.data.sync.InMemoryBundleBackend;
import com.pasich.mynotes.data.sync.RoomSyncStore;
import com.pasich.mynotes.data.sync.SyncMetadata;
import com.pasich.mynotes.data.sync.SyncResolution;
import com.pasich.mynotes.data.sync.SyncService;
import com.pasich.mynotes.data.sync.SyncState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Two real stores, one shared history, a conflict settled through the store's own resolution.
 *
 * <p>Reproduced on a Pixel with tasks: peer A deletes, peer B edits, B keeps the live version, and
 * the same conflict came back on every sync. Run for every record type: the protocol is shared, but
 * each type has its own DAO path through the store.
 */
@RunWith(AndroidJUnit4.class)
public class TwoPeerStoreConvergenceTest {

    private static final String STABLE_ID = "11111111-1111-4111-8111-111111111111";

    private Context context;
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-05T12:00:00Z"));
    private InMemoryBundleBackend drive;
    private Peer a;
    private Peer b;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        drive = new InMemoryBundleBackend(clock);
        a = new Peer();
        b = new Peer();
    }

    @After
    public void tearDown() {
        a.db.close();
        b.db.close();
        if (c != null) c.db.close();
    }

    private Peer c;

    @Test
    public void aRecordRevivedElsewhereLeavesNoPhantomDeletionOnAPeerThatNeverHeldIt()
            throws Exception {
        // B edits, C deletes, B keeps the edit. A — a fresh install that never held the record —
        // received the deletion-versus-edit conflict along the way. Once the edit arrives on A the
        // row is meaningless: offered anyway, pre-selected on the deletion, one tap deleted the
        // record on every device with no conflict raised anywhere.
        RecordKind kind = new TaskRecord();
        c = new Peer();
        kind.create(b, "TaskX");
        clock.advance();
        assertClean(b.sync());
        clock.advance();
        assertClean(c.sync());
        clock.advance();
        assertClean(b.sync());

        kind.edit(b, "TaskX-B");
        clock.advance();
        kind.delete(c);
        clock.advance();
        assertClean(c.sync());
        clock.advance();
        assertThat(b.sync().getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(b.store.getUnresolvedConflicts()).hasSize(1);
        clock.advance();
        assertThat(a.sync().getStatus()).isEqualTo(SyncState.Status.SUCCESS);

        // B keeps its edit: the winner was the deletion, so it is the alternative that stays.
        clock.advance();
        for (SyncConflictEntity conflict : b.store.getUnresolvedConflicts()) {
            b.store.resolveConflict(
                    conflict.id,
                    conflict.winnerTombstone
                            ? SyncResolution.KEEP_ALTERNATIVE
                            : SyncResolution.KEEP_WINNER);
        }
        clock.advance();
        assertClean(b.sync());
        clock.advance();
        assertClean(a.sync());

        // The record arrived on A; nothing is left there to tap, and it lives on everywhere.
        assertThat(a.store.getUnresolvedConflicts()).isEmpty();
        assertThat(kind.title(a)).isEqualTo("TaskX-B");
        clock.advance();
        assertClean(c.sync());
        assertThat(kind.title(c)).isEqualTo("TaskX-B");
        clock.advance();
        assertClean(a.sync());
        clock.advance();
        assertClean(b.sync());
        assertThat(kind.title(b)).isEqualTo("TaskX-B");
        assertThat(b.store.getUnresolvedConflicts()).isEmpty();
        assertThat(c.store.getUnresolvedConflicts()).isEmpty();
    }

    @Test
    public void aTaskDeletedOnOnePeerAndEditedOnTheOtherSettlesAfterOneResolution()
            throws Exception {
        settlesAfterOneResolution(new TaskRecord(), false);
    }

    @Test
    public void aTaskSyncedBeforeBasesWereRecordedStillSettlesAfterOneResolution()
            throws Exception {
        settlesAfterOneResolution(new TaskRecord(), true);
    }

    @Test
    public void aNoteDeletedOnOnePeerAndEditedOnTheOtherSettlesAfterOneResolution()
            throws Exception {
        settlesAfterOneResolution(new NoteRecord(), false);
    }

    @Test
    public void aTagDeletedOnOnePeerAndEditedOnTheOtherSettlesAfterOneResolution()
            throws Exception {
        settlesAfterOneResolution(new TagRecord(), false);
    }

    @Test
    public void aCategoryDeletedOnOnePeerAndEditedOnTheOtherSettlesAfterOneResolution()
            throws Exception {
        settlesAfterOneResolution(new CategoryRecord(), false);
    }

    private void settlesAfterOneResolution(RecordKind kind, boolean migratedWithoutBases)
            throws Exception {
        kind.create(a, "TaskX");
        clock.advance();
        assertClean(a.sync());
        clock.advance();
        assertClean(b.sync());
        clock.advance();
        assertClean(a.sync());
        assertThat(kind.title(b)).isEqualTo("TaskX");
        if (migratedWithoutBases) {
            // Rows a build that recorded no synced version left behind.
            a.forgetBases();
            b.forgetBases();
        }

        // A deletes and syncs; B edits and syncs.
        kind.delete(a);
        clock.advance();
        a.sync();
        clock.advance();
        kind.edit(b, "TaskX-B");
        clock.advance();
        assertThat(b.sync().getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(b.store.getUnresolvedConflicts()).isNotEmpty();

        // B keeps the live version — every open conflict, as the dialog offers them one by one.
        clock.advance();
        for (SyncConflictEntity conflict : b.store.getUnresolvedConflicts()) {
            b.store.resolveConflict(
                    conflict.id,
                    conflict.winnerTombstone
                            ? SyncResolution.KEEP_ALTERNATIVE
                            : SyncResolution.KEEP_WINNER);
        }
        assertThat(b.store.getUnresolvedConflicts()).isEmpty();
        clock.advance();
        SyncState publishing = b.sync();
        assertThat(publishing.getErrorMessage()).isNull();
        assertClean(publishing);
        assertThat(b.store.getUnresolvedConflicts()).isEmpty();
        clock.advance();
        assertClean(b.sync());
        assertThat(b.store.getUnresolvedConflicts()).isEmpty();

        // A gets the record back — the tombstone revival — with the edit, and stays clean.
        clock.advance();
        SyncState onA = a.sync();
        assertThat(onA.getErrorMessage()).isNull();
        assertClean(onA);
        assertThat(kind.title(a)).isEqualTo("TaskX-B");
        assertThat(kind.title(b)).isEqualTo("TaskX-B");
        // Nothing left to ask either user, including the deletion A reported against the version
        // it replaced when its synced version was still unknown.
        assertThat(a.store.getUnresolvedConflicts()).isEmpty();
        clock.advance();
        assertClean(a.sync());
        clock.advance();
        assertClean(b.sync());
        assertThat(a.store.getUnresolvedConflicts()).isEmpty();
        assertThat(b.store.getUnresolvedConflicts()).isEmpty();
    }

    private static void assertClean(SyncState state) {
        assertThat(state.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(state.getConflictCount()).isEqualTo(0);
    }

    // ------------------------------------------------------------------ peers and records

    private final class Peer {
        final AppDatabase db;
        final RoomSyncStore store;
        long localId;

        Peer() {
            db =
                    Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                            .allowMainThreadQueries()
                            .build();
            store = new RoomSyncStore(context, db, mock(PreferenceHelper.class));
        }

        SyncState sync() {
            return new SyncService(store, new com.pasich.mynotes.data.sync.SyncMerger(), clock)
                    .sync(drive);
        }

        void forgetBases() {
            db.getOpenHelper()
                    .getWritableDatabase()
                    .execSQL("UPDATE sync_metadata SET syncedVersionId = NULL");
        }

        void metadata(String recordType, long id) {
            localId = id;
            db.syncMetadataDao()
                    .insertIfAbsent(
                            new SyncMetadataEntity(
                                    recordType, id, STABLE_ID, clock.millis(), null));
        }

        void touch(String recordType) {
            db.syncMetadataDao().touch(recordType, localIdOf(recordType), clock.millis());
        }

        void markDeleted(String recordType) {
            db.syncMetadataDao().markDeleted(recordType, localIdOf(recordType), clock.millis());
        }

        long localIdOf(String recordType) {
            SyncMetadataEntity metadata = db.syncMetadataDao().getByStableId(recordType, STABLE_ID);
            return metadata == null ? -1L : metadata.localId;
        }
    }

    /** How one record type is created, edited, deleted and read through the peer's own DAOs. */
    private interface RecordKind {
        void create(Peer peer, String title);

        void edit(Peer peer, String title);

        void delete(Peer peer);

        String title(Peer peer);
    }

    private static final class TaskRecord implements RecordKind {
        @Override
        public void create(Peer peer, String title) {
            Task task = new Task(title, 0);
            task.setCreatedAt(1_000L);
            peer.metadata(SyncMetadata.RECORD_TYPE_TASK, peer.db.taskDao().insertTask(task));
        }

        @Override
        public void edit(Peer peer, String title) {
            Task task = peer.db.taskDao().getTaskSync((int) peer.localIdOf("task"));
            task.setTitle(title);
            peer.db.taskDao().updateTask(task);
            peer.touch(SyncMetadata.RECORD_TYPE_TASK);
        }

        @Override
        public void delete(Peer peer) {
            peer.db.taskDao().deleteById((int) peer.localIdOf("task"));
            peer.markDeleted(SyncMetadata.RECORD_TYPE_TASK);
        }

        @Override
        public String title(Peer peer) {
            long id = peer.localIdOf("task");
            Task task = id < 0 ? null : peer.db.taskDao().getTaskSync((int) id);
            return task == null ? null : task.getTitle();
        }
    }

    private static final class NoteRecord implements RecordKind {
        @Override
        public void create(Peer peer, String title) {
            Note note = new Note().create(title, "body", 1_000L, "");
            peer.metadata(SyncMetadata.RECORD_TYPE_NOTE, peer.db.noteDao().addNote(note));
        }

        @Override
        public void edit(Peer peer, String title) {
            Note note = peer.db.noteDao().getNoteSync((int) peer.localIdOf("note"));
            note.setTitle(title);
            peer.db.noteDao().addNote(note);
            peer.touch(SyncMetadata.RECORD_TYPE_NOTE);
        }

        @Override
        public void delete(Peer peer) {
            peer.db.noteDao().deleteById((int) peer.localIdOf("note"));
            peer.markDeleted(SyncMetadata.RECORD_TYPE_NOTE);
        }

        @Override
        public String title(Peer peer) {
            long id = peer.localIdOf("note");
            Note note = id < 0 ? null : peer.db.noteDao().getNoteSync((int) id);
            return note == null ? null : note.getTitle();
        }
    }

    private static final class TagRecord implements RecordKind {
        @Override
        public void create(Peer peer, String title) {
            peer.metadata(
                    SyncMetadata.RECORD_TYPE_TAG,
                    peer.db.tagsDao().addTag(new Tag().create(title)));
        }

        @Override
        public void edit(Peer peer, String title) {
            Tag tag = peer.db.tagsDao().getTagSync(peer.localIdOf("tag"));
            tag.setNameTag(title);
            peer.db.tagsDao().updateTag(tag);
            peer.touch(SyncMetadata.RECORD_TYPE_TAG);
        }

        @Override
        public void delete(Peer peer) {
            peer.db.tagsDao().deleteById(peer.localIdOf("tag"));
            peer.markDeleted(SyncMetadata.RECORD_TYPE_TAG);
        }

        @Override
        public String title(Peer peer) {
            long id = peer.localIdOf("tag");
            Tag tag = id < 0 ? null : peer.db.tagsDao().getTagSync(id);
            return tag == null ? null : tag.getNameTag();
        }
    }

    private static final class CategoryRecord implements RecordKind {
        @Override
        public void create(Peer peer, String title) {
            TaskCategory category = new TaskCategory();
            category.setName(title);
            peer.metadata(
                    SyncMetadata.RECORD_TYPE_CATEGORY,
                    peer.db.taskCategoryDao().insertCategory(category));
        }

        @Override
        public void edit(Peer peer, String title) {
            TaskCategory category =
                    peer.db.taskCategoryDao().getCategorySync((int) peer.localIdOf("category"));
            category.setName(title);
            peer.db.taskCategoryDao().updateCategory(category);
            peer.touch(SyncMetadata.RECORD_TYPE_CATEGORY);
        }

        @Override
        public void delete(Peer peer) {
            peer.db.taskCategoryDao().deleteById((int) peer.localIdOf("category"));
            peer.markDeleted(SyncMetadata.RECORD_TYPE_CATEGORY);
        }

        @Override
        public String title(Peer peer) {
            long id = peer.localIdOf("category");
            TaskCategory category =
                    id < 0 ? null : peer.db.taskCategoryDao().getCategorySync((int) id);
            return category == null ? null : category.getName();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            now = start;
        }

        void advance() {
            now = now.plus(Duration.ofMinutes(1));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
