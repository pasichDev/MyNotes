package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.data.database.dao.NoteDao;
import com.pasich.mynotes.data.database.dao.SyncMetadataDao;
import com.pasich.mynotes.data.database.dao.TagsDao;
import com.pasich.mynotes.data.database.dao.TaskCategoryDao;
import com.pasich.mynotes.data.database.dao.TaskDao;
import com.pasich.mynotes.data.database.dao.Transactions;
import com.pasich.mynotes.data.database.entities.SyncMetadataEntity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class SyncMutationCoordinatorTest {

    private NoteDao noteDao;
    private TaskDao taskDao;
    private TagsDao tagsDao;
    private TaskCategoryDao taskCategoryDao;
    private Transactions transactions;
    private FakeSyncMetadataDao syncMetadataDao;
    private SyncMutationCoordinator coordinator;

    @Before
    public void setUp() {
        noteDao = mock(NoteDao.class);
        taskDao = mock(TaskDao.class);
        tagsDao = mock(TagsDao.class);
        taskCategoryDao = mock(TaskCategoryDao.class);
        transactions = mock(Transactions.class);
        syncMetadataDao = new FakeSyncMetadataDao();
        coordinator =
                new SyncMutationCoordinator(
                        new SyncMutationCoordinator.TransactionExecutor() {
                            @Override
                            public <T> T run(
                                    SyncMutationCoordinator.TransactionCallable<T> callable) {
                                return callable.call();
                            }
                        },
                        noteDao,
                        taskDao,
                        tagsDao,
                        taskCategoryDao,
                        transactions,
                        syncMetadataDao,
                        new FixedTimeProvider(1_000L),
                        new QueueStableIdGenerator("stable-a", "stable-b", "stable-c"));
    }

    @Test
    public void insertNote_createsMetadataRowWithStableIdAndTimestamp() {
        Note note = new Note().create("Title", "Body", 10L, "");
        when(noteDao.addNote(note)).thenReturn(41L);

        long insertedId = coordinator.insertNote(note);

        assertThat(insertedId).isEqualTo(41L);
        assertThat(note.getId()).isEqualTo(41);
        SyncMetadataEntity metadata = syncMetadataDao.get(SyncMetadata.RECORD_TYPE_NOTE, 41L);
        assertThat(metadata.stableId).isEqualTo("stable-a");
        assertThat(metadata.updatedAt).isEqualTo(1_000L);
        assertThat(metadata.deletedAt).isNull();
    }

    @Test
    public void updateNoteContent_reusesExistingStableIdAndTouchesMetadata() {
        syncMetadataDao.insertIfAbsent(
                new SyncMetadataEntity(
                        SyncMetadata.RECORD_TYPE_NOTE, 7L, "seed-stable", 80L, null));
        Note note = new Note().create("New", "Body", 10L, "work");
        note.setId(7);
        note.setValueJson("{}");
        note.setAttachments("[]");

        coordinator.updateNoteContent(note);

        verify(noteDao)
                .updateNoteContent(
                        eq(7), eq("New"), eq("Body"), eq("{}"), eq(10L), eq("work"), eq("[]"));
        SyncMetadataEntity metadata = syncMetadataDao.get(SyncMetadata.RECORD_TYPE_NOTE, 7L);
        assertThat(metadata.stableId).isEqualTo("seed-stable");
        assertThat(metadata.updatedAt).isEqualTo(1_000L);
        assertThat(metadata.deletedAt).isNull();
    }

    @Test
    public void deleteNote_keepsTombstoneAndMarksDeletionTimestamp() {
        syncMetadataDao.insertIfAbsent(
                new SyncMetadataEntity(
                        SyncMetadata.RECORD_TYPE_NOTE, 9L, "seed-stable", 90L, null));
        Note note = new Note().create("Trash", "Body", 10L, "");
        note.setId(9);

        coordinator.deleteNote(note);

        verify(noteDao).deleteNote(note);
        SyncMetadataEntity metadata = syncMetadataDao.get(SyncMetadata.RECORD_TYPE_NOTE, 9L);
        assertThat(metadata.stableId).isEqualTo("seed-stable");
        assertThat(metadata.updatedAt).isEqualTo(1_000L);
        assertThat(metadata.deletedAt).isEqualTo(1_000L);
    }

    @Test
    public void repeatedUpdate_keepsMetadataMonotonicWhenClockMovesBackward() {
        syncMetadataDao.insertIfAbsent(
                new SyncMetadataEntity(
                        SyncMetadata.RECORD_TYPE_NOTE, 11L, "seed-stable", 1_500L, null));
        Note note = new Note().create("Repeat", "Body", 10L, "");
        note.setId(11);
        note.setAttachments("");

        coordinator.updateNoteContent(note);
        coordinator.updateNoteContent(note);

        SyncMetadataEntity metadata = syncMetadataDao.get(SyncMetadata.RECORD_TYPE_NOTE, 11L);
        assertThat(metadata.updatedAt).isEqualTo(1_502L);
        assertThat(metadata.deletedAt).isNull();
    }

    @Test
    public void deleteAllTrashNotes_marksEveryDeletedNoteWithSameBatchTimestamp() {
        when(noteDao.getTrashNoteIdsSync()).thenReturn(List.of(3, 4));

        coordinator.deleteAllTrashNotes();

        verify(noteDao).deleteAllTrashNotes();
        SyncMetadataEntity first = syncMetadataDao.get(SyncMetadata.RECORD_TYPE_NOTE, 3L);
        SyncMetadataEntity second = syncMetadataDao.get(SyncMetadata.RECORD_TYPE_NOTE, 4L);
        assertThat(first.stableId).isEqualTo("stable-a");
        assertThat(second.stableId).isEqualTo("stable-b");
        assertThat(first.updatedAt).isEqualTo(1_000L);
        assertThat(second.updatedAt).isEqualTo(1_000L);
        assertThat(first.deletedAt).isEqualTo(1_000L);
        assertThat(second.deletedAt).isEqualTo(1_000L);
    }

    @Test
    public void insertNotes_usesSingleImportTimestampForWholeBatch() {
        List<Note> notes = new ArrayList<>();
        notes.add(new Note().create("One", "1", 1L, ""));
        notes.add(new Note().create("Two", "2", 2L, ""));
        when(noteDao.addNotes(anyList())).thenReturn(new long[] {101L, 102L});

        coordinator.insertNotes(notes);

        SyncMetadataEntity first = syncMetadataDao.get(SyncMetadata.RECORD_TYPE_NOTE, 101L);
        SyncMetadataEntity second = syncMetadataDao.get(SyncMetadata.RECORD_TYPE_NOTE, 102L);
        assertThat(first.updatedAt).isEqualTo(1_000L);
        assertThat(second.updatedAt).isEqualTo(1_000L);
        assertThat(first.deletedAt).isNull();
        assertThat(second.deletedAt).isNull();
        assertThat(notes.get(0).getId()).isEqualTo(101);
        assertThat(notes.get(1).getId()).isEqualTo(102);
    }

    @Test
    public void updateNote_onADeviceWithABackwardsClockStillOutranksWhatItSynced() {
        // Merging is last-write-wins on wall-clock time, which reads like "the device whose clock
        // runs slow always loses". It does not: applySnapshot copies the winner's timestamp into
        // local metadata, and touch() then assigns max(now, stored + 1). So an edit made after
        // seeing a newer remote version wins even when this device's clock is hours behind.
        // This device's clock reads 1_000; the record it synced carries 5_000 from a device whose
        // clock runs ahead.
        long remoteTimestamp = 5_000L;
        syncMetadataDao.insertIfAbsent(
                new SyncMetadataEntity(
                        SyncMetadata.RECORD_TYPE_NOTE, 1L, "stable-a", remoteTimestamp, null));

        Note note = new Note().create("Edited here", "text", 1L, "");
        note.setId(1);
        coordinator.updateNoteContent(note);

        SyncMetadataEntity metadata = syncMetadataDao.get(SyncMetadata.RECORD_TYPE_NOTE, 1L);
        assertThat(metadata.updatedAt).isGreaterThan(remoteTimestamp);
    }

    @Test
    public void insertNotes_keepsBackupIdsWhenNothingOccupiesThem() {
        // The ordinary restore, and the one after a reinstall: an empty library, so every note
        // keeps the ID it had when the backup was taken.
        List<Note> notes = new ArrayList<>();
        Note restored = new Note().create("One", "1", 1L, "");
        restored.setId(7);
        notes.add(restored);
        when(noteDao.getNoteSync(7)).thenReturn(null);
        when(noteDao.addNotes(anyList())).thenReturn(new long[] {7L});

        coordinator.insertNotes(notes);

        assertThat(notes.get(0).getId()).isEqualTo(7);
        assertThat(syncMetadataDao.get(SyncMetadata.RECORD_TYPE_NOTE, 7L)).isNotNull();
    }

    @Test
    public void insertNotes_doesNotOverwriteAnExistingNoteThatHoldsTheSameId() {
        // addNotes is a REPLACE insert and backups carry their original IDs, so restoring onto a
        // device that already has notes used to destroy every colliding one — and hand its stable
        // ID to the replacement, propagating the loss to every other device. The restored note is
        // inserted as a new row instead; nothing existing is touched.
        List<Note> notes = new ArrayList<>();
        Note restored = new Note().create("Restored", "r", 1L, "");
        restored.setId(7);
        notes.add(restored);
        Note occupant = new Note().create("Already here", "x", 2L, "");
        occupant.setId(7);
        when(noteDao.getNoteSync(7)).thenReturn(occupant);
        when(noteDao.addNotes(anyList())).thenReturn(new long[] {42L});

        coordinator.insertNotes(notes);

        assertThat(notes.get(0).getId()).isEqualTo(42);
        assertThat(syncMetadataDao.get(SyncMetadata.RECORD_TYPE_NOTE, 42L)).isNotNull();
    }

    @Test
    public void deleteTask_keepsTombstoneAndMarksDeletionTimestamp() {
        syncMetadataDao.insertIfAbsent(
                new SyncMetadataEntity(
                        SyncMetadata.RECORD_TYPE_TASK, 12L, "task-stable", 90L, null));
        Task task = new Task();
        task.setId(12);

        coordinator.deleteTask(task);

        verify(taskDao).deleteTask(task);
        SyncMetadataEntity metadata = syncMetadataDao.get(SyncMetadata.RECORD_TYPE_TASK, 12L);
        assertThat(metadata.stableId).isEqualTo("task-stable");
        assertThat(metadata.updatedAt).isEqualTo(1_000L);
        assertThat(metadata.deletedAt).isEqualTo(1_000L);
    }

    @Test
    public void deleteCategory_keepsTombstoneAndMarksDeletionTimestamp() {
        syncMetadataDao.insertIfAbsent(
                new SyncMetadataEntity(
                        SyncMetadata.RECORD_TYPE_CATEGORY, 13L, "category-stable", 90L, null));
        TaskCategory category = new TaskCategory();
        category.setId(13);

        coordinator.deleteCategory(category);

        verify(taskCategoryDao).deleteCategory(category);
        SyncMetadataEntity metadata = syncMetadataDao.get(SyncMetadata.RECORD_TYPE_CATEGORY, 13L);
        assertThat(metadata.stableId).isEqualTo("category-stable");
        assertThat(metadata.updatedAt).isEqualTo(1_000L);
        assertThat(metadata.deletedAt).isEqualTo(1_000L);
    }

    @Test
    public void renameTag_touchesTagAndAllNotesChangedByRename() {
        syncMetadataDao.insertIfAbsent(
                new SyncMetadataEntity(SyncMetadata.RECORD_TYPE_TAG, 14L, "tag-stable", 90L, null));
        syncMetadataDao.insertIfAbsent(
                new SyncMetadataEntity(
                        SyncMetadata.RECORD_TYPE_NOTE, 15L, "note-stable", 90L, null));
        Tag tag = new Tag().create("old");
        tag.id = 14L;
        when(transactions.getNoteIdsForTag("old")).thenReturn(List.of(15));

        coordinator.renameTag(tag, "new");

        verify(transactions).renameTagNotes("old", "new");
        verify(transactions).updateTagName("new", 14L);
        assertThat(syncMetadataDao.get(SyncMetadata.RECORD_TYPE_TAG, 14L).updatedAt)
                .isEqualTo(1_000L);
        assertThat(syncMetadataDao.get(SyncMetadata.RECORD_TYPE_NOTE, 15L).updatedAt)
                .isEqualTo(1_000L);
    }

    private static final class FixedTimeProvider implements SyncMutationCoordinator.TimeProvider {
        private final long value;

        private FixedTimeProvider(long value) {
            this.value = value;
        }

        @Override
        public long now() {
            return value;
        }
    }

    private static final class QueueStableIdGenerator
            implements SyncMutationCoordinator.StableIdGenerator {
        private final ArrayDeque<String> values;

        private QueueStableIdGenerator(String... values) {
            this.values = new ArrayDeque<>(List.of(values));
        }

        @Override
        public String nextStableId() {
            return values.removeFirst();
        }
    }

    private static final class FakeSyncMetadataDao implements SyncMetadataDao {
        private final Map<String, SyncMetadataEntity> rows = new LinkedHashMap<>();

        @Override
        public long insertIfAbsent(SyncMetadataEntity metadata) {
            String key = key(metadata.recordType, metadata.localId);
            if (rows.containsKey(key)) return -1L;
            rows.put(key, copy(metadata));
            return 1L;
        }

        @Override
        public void setVersion(String recordType, long localId, long updatedAt, Long deletedAt) {
            SyncMetadataEntity current = get(recordType, localId);
            rows.put(
                    key(recordType, localId),
                    new SyncMetadataEntity(
                            recordType, localId, current.stableId, updatedAt, deletedAt));
        }

        @Override
        public SyncMetadataEntity get(String recordType, long localId) {
            SyncMetadataEntity value = rows.get(key(recordType, localId));
            return value == null ? null : copy(value);
        }

        @Override
        public boolean exists(String recordType, long localId) {
            return rows.containsKey(key(recordType, localId));
        }

        @Override
        public SyncMetadataEntity getByStableId(String recordType, String stableId) {
            for (SyncMetadataEntity value : rows.values()) {
                if (value.recordType.equals(recordType) && value.stableId.equals(stableId)) {
                    return copy(value);
                }
            }
            return null;
        }

        @Override
        public List<SyncMetadataEntity> getLiveRecords() {
            List<SyncMetadataEntity> live = new ArrayList<>();
            for (SyncMetadataEntity value : rows.values()) {
                if (value.deletedAt == null) live.add(copy(value));
            }
            return live;
        }

        @Override
        public List<SyncMetadataEntity> getTombstones() {
            List<SyncMetadataEntity> tombstones = new ArrayList<>();
            for (SyncMetadataEntity value : rows.values()) {
                if (value.deletedAt != null) tombstones.add(copy(value));
            }
            return tombstones;
        }

        @Override
        public List<SyncMetadataEntity> getAll() {
            List<SyncMetadataEntity> all = new ArrayList<>();
            for (SyncMetadataEntity value : rows.values()) {
                all.add(copy(value));
            }
            return all;
        }

        @Override
        public List<SyncMetadataEntity> getChangedSince(long timestamp) {
            List<SyncMetadataEntity> changed = new ArrayList<>();
            for (SyncMetadataEntity value : rows.values()) {
                if (value.updatedAt > timestamp) changed.add(copy(value));
            }
            return changed;
        }

        @Override
        public void touch(String recordType, long localId, long nowMillis) {
            SyncMetadataEntity current = rows.get(key(recordType, localId));
            long nextUpdatedAt = SyncMetadata.nextUpdatedAt(current.updatedAt, nowMillis);
            rows.put(
                    key(recordType, localId),
                    new SyncMetadataEntity(
                            recordType, localId, current.stableId, nextUpdatedAt, null));
        }

        @Override
        public void markDeleted(String recordType, long localId, long nowMillis) {
            SyncMetadataEntity current = rows.get(key(recordType, localId));
            long nextUpdatedAt = SyncMetadata.nextUpdatedAt(current.updatedAt, nowMillis);
            rows.put(
                    key(recordType, localId),
                    new SyncMetadataEntity(
                            recordType, localId, current.stableId, nextUpdatedAt, nextUpdatedAt));
        }

        private static SyncMetadataEntity copy(SyncMetadataEntity metadata) {
            return new SyncMetadataEntity(
                    metadata.recordType,
                    metadata.localId,
                    metadata.stableId,
                    metadata.updatedAt,
                    metadata.deletedAt);
        }

        private static String key(String recordType, long localId) {
            return recordType + ":" + localId;
        }
    }
}
