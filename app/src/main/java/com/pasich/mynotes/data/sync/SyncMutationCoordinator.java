package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import com.pasich.mynotes.data.database.AppDatabase;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Coordinates local mutations with sync metadata updates inside one Room transaction. */
@Singleton
public class SyncMutationCoordinator {
    private static final long LEGACY_IMPORT_WINDOW_MILLIS = 5_000L;

    interface TransactionCallable<T> {
        T call();
    }

    interface TransactionExecutor {
        <T> T run(@NonNull TransactionCallable<T> callable);
    }

    interface TimeProvider {
        long now();
    }

    interface StableIdGenerator {
        @NonNull
        String nextStableId();
    }

    private final TransactionExecutor transactionExecutor;
    private final NoteDao noteDao;
    private final TaskDao taskDao;
    private final TagsDao tagsDao;
    private final TaskCategoryDao taskCategoryDao;
    private final Transactions transactions;
    private final SyncMetadataDao syncMetadataDao;
    private final TimeProvider timeProvider;
    private final StableIdGenerator stableIdGenerator;
    private final Object legacyImportLock = new Object();
    private long legacyImportTimestamp = -1L;
    private long legacyImportExpiresAt = -1L;

    @Inject
    public SyncMutationCoordinator(@NonNull AppDatabase database) {
        this(
                new TransactionExecutor() {
                    @Override
                    public <T> T run(@NonNull TransactionCallable<T> callable) {
                        return database.runInTransaction(
                                new Callable<T>() {
                                    @Override
                                    public T call() {
                                        return callable.call();
                                    }
                                });
                    }
                },
                database.noteDao(),
                database.taskDao(),
                database.tagsDao(),
                database.taskCategoryDao(),
                database.transactionsNote(),
                database.syncMetadataDao(),
                System::currentTimeMillis,
                SyncMetadata::newStableId);
    }

    SyncMutationCoordinator(
            @NonNull TransactionExecutor transactionExecutor,
            @NonNull NoteDao noteDao,
            @NonNull TaskDao taskDao,
            @NonNull TagsDao tagsDao,
            @NonNull TaskCategoryDao taskCategoryDao,
            @NonNull Transactions transactions,
            @NonNull SyncMetadataDao syncMetadataDao,
            @NonNull TimeProvider timeProvider,
            @NonNull StableIdGenerator stableIdGenerator) {
        this.transactionExecutor = transactionExecutor;
        this.noteDao = noteDao;
        this.taskDao = taskDao;
        this.tagsDao = tagsDao;
        this.taskCategoryDao = taskCategoryDao;
        this.transactions = transactions;
        this.syncMetadataDao = syncMetadataDao;
        this.timeProvider = timeProvider;
        this.stableIdGenerator = stableIdGenerator;
    }

    public long insertTag(@NonNull Tag tag) {
        return transactionExecutor.run(
                () -> {
                    long insertedId = tagsDao.addTag(tag);
                    long localId = resolveLongId(tag.getId(), insertedId);
                    tag.id = localId;
                    touchRecord(SyncMetadata.RECORD_TYPE_TAG, localId, timeProvider.now());
                    return insertedId;
                });
    }

    public void insertTags(List<Tag> tags) {
        if (tags == null || tags.isEmpty()) return;
        transactionExecutor.run(
                () -> {
                    long timestamp =
                            resolveBatchTimestamp(
                                    SyncMetadata.RECORD_TYPE_TAG, extractTagIds(tags));
                    releaseTakenTagIds(tags);
                    long[] insertedIds = tagsDao.addTags(tags);
                    for (int i = 0; i < tags.size(); i++) {
                        Tag tag = tags.get(i);
                        long localId = resolveLongId(tag.getId(), insertedIds[i]);
                        tag.id = localId;
                        touchRecord(SyncMetadata.RECORD_TYPE_TAG, localId, timestamp);
                    }
                    return null;
                });
    }

    public void updateTag(@NonNull Tag tag) {
        transactionExecutor.run(
                () -> {
                    tagsDao.updateTag(tag);
                    touchRecord(SyncMetadata.RECORD_TYPE_TAG, tag.getId(), timeProvider.now());
                    return null;
                });
    }

    public void updateTags(List<Tag> tags) {
        if (tags == null || tags.isEmpty()) return;
        transactionExecutor.run(
                () -> {
                    long timestamp = timeProvider.now();
                    tagsDao.updateTags(tags);
                    for (Tag tag : tags) {
                        touchRecord(SyncMetadata.RECORD_TYPE_TAG, tag.getId(), timestamp);
                    }
                    return null;
                });
    }

    public void deleteTag(@NonNull Tag tag) {
        transactionExecutor.run(
                () -> {
                    tagsDao.deleteTag(tag);
                    markDeletedRecord(
                            SyncMetadata.RECORD_TYPE_TAG, tag.getId(), timeProvider.now());
                    return null;
                });
    }

    public void renameTag(@NonNull Tag tag, @NonNull String newName) {
        transactionExecutor.run(
                () -> {
                    long timestamp = timeProvider.now();
                    List<Integer> affectedNoteIds = transactions.getNoteIdsForTag(tag.getNameTag());
                    transactions.renameTagNotes(tag.getNameTag(), newName);
                    transactions.updateTagName(newName, tag.getId());
                    touchRecord(SyncMetadata.RECORD_TYPE_TAG, tag.getId(), timestamp);
                    touchRecords(SyncMetadata.RECORD_TYPE_NOTE, affectedNoteIds, timestamp);
                    tag.setNameTag(newName);
                    return null;
                });
    }

    public void deleteTagButKeepNotes(@NonNull Tag tag) {
        transactionExecutor.run(
                () -> {
                    long timestamp = timeProvider.now();
                    List<Integer> affectedNoteIds = transactions.getNoteIdsForTag(tag.getNameTag());
                    transactions.clearTagInNotes(tag.getNameTag());
                    tagsDao.deleteTag(tag);
                    touchRecords(SyncMetadata.RECORD_TYPE_NOTE, affectedNoteIds, timestamp);
                    markDeletedRecord(SyncMetadata.RECORD_TYPE_TAG, tag.getId(), timestamp);
                    return null;
                });
    }

    public void deleteTagAndMoveNotesToTrash(@NonNull Tag tag) {
        transactionExecutor.run(
                () -> {
                    long timestamp = timeProvider.now();
                    List<Integer> affectedNoteIds = transactions.getNoteIdsForTag(tag.getNameTag());
                    transactions.moveNotesWithTagToTrash(tag.getNameTag());
                    transactions.clearTagInNotes(tag.getNameTag());
                    tagsDao.deleteTag(tag);
                    touchRecords(SyncMetadata.RECORD_TYPE_NOTE, affectedNoteIds, timestamp);
                    markDeletedRecord(SyncMetadata.RECORD_TYPE_TAG, tag.getId(), timestamp);
                    return null;
                });
    }

    public long insertNote(@NonNull Note note) {
        return transactionExecutor.run(() -> insertNoteInternal(note, timeProvider.now()));
    }

    public void insertNotes(List<Note> notes) {
        if (notes == null || notes.isEmpty()) return;
        transactionExecutor.run(
                () -> {
                    long timestamp =
                            resolveBatchTimestamp(
                                    SyncMetadata.RECORD_TYPE_NOTE, extractNoteIds(notes));
                    releaseTakenNoteIds(notes);
                    long[] insertedIds = noteDao.addNotes(notes);
                    for (int i = 0; i < notes.size(); i++) {
                        Note note = notes.get(i);
                        int localId = resolveIntId(note.getId(), insertedIds[i]);
                        note.setId(localId);
                        touchRecord(SyncMetadata.RECORD_TYPE_NOTE, localId, timestamp);
                    }
                    return null;
                });
    }

    public void updateNoteContent(@NonNull Note note) {
        transactionExecutor.run(
                () -> {
                    noteDao.updateNoteContent(
                            note.getId(),
                            note.getTitle(),
                            note.getValue(),
                            note.getValueJson(),
                            note.getDate(),
                            note.getTag(),
                            note.getAttachments());
                    touchRecord(SyncMetadata.RECORD_TYPE_NOTE, note.getId(), timeProvider.now());
                    return null;
                });
    }

    public void deleteNote(@NonNull Note note) {
        transactionExecutor.run(
                () -> {
                    noteDao.deleteNote(note);
                    markDeletedRecord(
                            SyncMetadata.RECORD_TYPE_NOTE, note.getId(), timeProvider.now());
                    return null;
                });
    }

    public void deleteNotes(List<Note> notes) {
        if (notes == null || notes.isEmpty()) return;
        transactionExecutor.run(
                () -> {
                    long timestamp = timeProvider.now();
                    for (Note note : notes) {
                        noteDao.deleteNote(note);
                        markDeletedRecord(SyncMetadata.RECORD_TYPE_NOTE, note.getId(), timestamp);
                    }
                    return null;
                });
    }

    public void moveNoteToTrash(int noteId) {
        moveNotesToTrash(Collections.singletonList(noteId));
    }

    public void moveNotesToTrash(List<Integer> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) return;
        transactionExecutor.run(
                () -> {
                    long timestamp = timeProvider.now();
                    noteDao.moveNotesToTrash(noteIds);
                    touchRecords(SyncMetadata.RECORD_TYPE_NOTE, noteIds, timestamp);
                    return null;
                });
    }

    public void restoreNoteFromTrash(int noteId) {
        restoreNotesFromTrash(Collections.singletonList(noteId));
    }

    public void restoreNotesFromTrash(List<Integer> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) return;
        transactionExecutor.run(
                () -> {
                    long timestamp = timeProvider.now();
                    noteDao.restoreNotesFromTrash(noteIds);
                    touchRecords(SyncMetadata.RECORD_TYPE_NOTE, noteIds, timestamp);
                    return null;
                });
    }

    public void deleteAllTrashNotes() {
        transactionExecutor.run(
                () -> {
                    List<Integer> trashNoteIds = noteDao.getTrashNoteIdsSync();
                    if (trashNoteIds.isEmpty()) return null;
                    long timestamp = timeProvider.now();
                    noteDao.deleteAllTrashNotes();
                    markDeletedRecords(SyncMetadata.RECORD_TYPE_NOTE, trashNoteIds, timestamp);
                    return null;
                });
    }

    public void setTagForNotes(@NonNull String tag, List<Integer> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) return;
        transactionExecutor.run(
                () -> {
                    long timestamp = timeProvider.now();
                    noteDao.setTagForNotes(tag, noteIds);
                    touchRecords(SyncMetadata.RECORD_TYPE_NOTE, noteIds, timestamp);
                    return null;
                });
    }

    public void setTagNote(@NonNull String tag, int noteId) {
        transactionExecutor.run(
                () -> {
                    noteDao.setTagNote(tag, noteId);
                    touchRecord(SyncMetadata.RECORD_TYPE_NOTE, noteId, timeProvider.now());
                    return null;
                });
    }

    public void restoreNotesAndFixTags(List<Integer> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) return;
        transactionExecutor.run(
                () -> {
                    long timestamp = timeProvider.now();
                    Set<Integer> affectedNoteIds =
                            new LinkedHashSet<>(transactions.getNoteIdsWithInvalidTags());
                    affectedNoteIds.addAll(noteIds);
                    transactions.clearInvalidTags();
                    transactions.restoreNotesInternal(noteIds);
                    touchRecords(
                            SyncMetadata.RECORD_TYPE_NOTE,
                            new ArrayList<>(affectedNoteIds),
                            timestamp);
                    return null;
                });
    }

    public void clearReminder(int noteId) {
        transactionExecutor.run(
                () -> {
                    noteDao.clearReminderSync(noteId);
                    touchRecord(SyncMetadata.RECORD_TYPE_NOTE, noteId, timeProvider.now());
                    return null;
                });
    }

    public void updateNoteReminder(int noteId, long reminderTime, @NonNull String repeat) {
        transactionExecutor.run(
                () -> {
                    noteDao.updateReminderSync(noteId, reminderTime, repeat);
                    touchRecord(SyncMetadata.RECORD_TYPE_NOTE, noteId, timeProvider.now());
                    return null;
                });
    }

    public void updateNoteReminderFull(
            int noteId, long reminderTime, @NonNull String repeat, int intervalMinutes) {
        transactionExecutor.run(
                () -> {
                    noteDao.updateReminderFullSync(noteId, reminderTime, repeat, intervalMinutes);
                    touchRecord(SyncMetadata.RECORD_TYPE_NOTE, noteId, timeProvider.now());
                    return null;
                });
    }

    public void setPinNote(int noteId, boolean pinned) {
        transactionExecutor.run(
                () -> {
                    noteDao.setPinNoteSync(noteId, pinned);
                    touchRecord(SyncMetadata.RECORD_TYPE_NOTE, noteId, timeProvider.now());
                    return null;
                });
    }

    public long insertTask(@NonNull Task task) {
        return transactionExecutor.run(
                () -> {
                    long insertedId = taskDao.insertTask(task);
                    int localId = resolveIntId(task.getId(), insertedId);
                    task.setId(localId);
                    touchRecord(SyncMetadata.RECORD_TYPE_TASK, localId, timeProvider.now());
                    return insertedId;
                });
    }

    public void updateTask(@NonNull Task task) {
        transactionExecutor.run(
                () -> {
                    taskDao.updateTask(task);
                    touchRecord(SyncMetadata.RECORD_TYPE_TASK, task.getId(), timeProvider.now());
                    return null;
                });
    }

    public void deleteTask(@NonNull Task task) {
        transactionExecutor.run(
                () -> {
                    taskDao.deleteTask(task);
                    markDeletedRecord(
                            SyncMetadata.RECORD_TYPE_TASK, task.getId(), timeProvider.now());
                    return null;
                });
    }

    public void setTaskDone(int taskId, boolean isDone) {
        transactionExecutor.run(
                () -> {
                    taskDao.setTaskDone(taskId, isDone ? 1 : 0);
                    touchRecord(SyncMetadata.RECORD_TYPE_TASK, taskId, timeProvider.now());
                    return null;
                });
    }

    public void clearCompletedTasks() {
        transactionExecutor.run(
                () -> {
                    List<Integer> completedTaskIds = taskDao.getCompletedTaskIdsSync();
                    if (completedTaskIds.isEmpty()) return null;
                    long timestamp = timeProvider.now();
                    taskDao.clearCompletedTasks();
                    markDeletedRecords(SyncMetadata.RECORD_TYPE_TASK, completedTaskIds, timestamp);
                    return null;
                });
    }

    public void setTaskReminder(int taskId, long time) {
        transactionExecutor.run(
                () -> {
                    taskDao.setTaskReminder(taskId, time);
                    touchRecord(SyncMetadata.RECORD_TYPE_TASK, taskId, timeProvider.now());
                    return null;
                });
    }

    public void setTaskReminderFull(int taskId, long time, int intervalMinutes) {
        transactionExecutor.run(
                () -> {
                    taskDao.setTaskReminderFullSync(taskId, time, intervalMinutes);
                    touchRecord(SyncMetadata.RECORD_TYPE_TASK, taskId, timeProvider.now());
                    return null;
                });
    }

    public void clearTaskReminder(int taskId) {
        transactionExecutor.run(
                () -> {
                    taskDao.clearTaskReminder(taskId);
                    touchRecord(SyncMetadata.RECORD_TYPE_TASK, taskId, timeProvider.now());
                    return null;
                });
    }

    public long insertCategory(@NonNull TaskCategory category) {
        return transactionExecutor.run(
                () -> {
                    long insertedId = taskCategoryDao.insertCategory(category);
                    int localId = resolveIntId(category.getId(), insertedId);
                    category.setId(localId);
                    touchRecord(SyncMetadata.RECORD_TYPE_CATEGORY, localId, timeProvider.now());
                    return insertedId;
                });
    }

    public void updateCategory(@NonNull TaskCategory category) {
        transactionExecutor.run(
                () -> {
                    taskCategoryDao.updateCategory(category);
                    touchRecord(
                            SyncMetadata.RECORD_TYPE_CATEGORY,
                            category.getId(),
                            timeProvider.now());
                    return null;
                });
    }

    public void deleteCategory(@NonNull TaskCategory category) {
        transactionExecutor.run(
                () -> {
                    taskCategoryDao.deleteCategory(category);
                    markDeletedRecord(
                            SyncMetadata.RECORD_TYPE_CATEGORY,
                            category.getId(),
                            timeProvider.now());
                    return null;
                });
    }

    private long insertNoteInternal(@NonNull Note note, long timestamp) {
        long insertedId = noteDao.addNote(note);
        int localId = resolveIntId(note.getId(), insertedId);
        note.setId(localId);
        touchRecord(SyncMetadata.RECORD_TYPE_NOTE, localId, timestamp);
        return insertedId;
    }

    /**
     * Lets a restore keep its original IDs only where they are still free.
     *
     * <p>Backups carry the IDs the notes had when the backup was taken, and {@code addNotes} is a
     * REPLACE insert. Restoring onto a device that already holds notes therefore destroyed every
     * note whose ID happened to collide — silently, with no way back. Sync made that worse: the
     * restored content inherited the destroyed note's stable ID through {@code ensureMetadataRow},
     * {@code touch()} cleared its tombstone, and the replacement propagated to every other device,
     * overwriting the cloud copy too.
     *
     * <p>A colliding note is now inserted as a new row instead. Restoring onto an empty library —
     * the ordinary case, and the one after a reinstall — still preserves every ID exactly.
     */
    private void releaseTakenNoteIds(@NonNull List<Note> notes) {
        for (Note note : notes) {
            if (note.getId() > 0 && noteDao.getNoteSync(note.getId()) != null) {
                note.setId(0);
            }
        }
    }

    /** Same protection for a restored tag list; see {@link #releaseTakenNoteIds}. */
    private void releaseTakenTagIds(@NonNull List<Tag> tags) {
        for (Tag tag : tags) {
            if (tag.getId() > 0 && tagsDao.getTagSync(tag.getId()) != null) {
                tag.id = 0;
            }
        }
    }

    private void touchRecords(@NonNull String recordType, List<Integer> localIds, long timestamp) {
        if (localIds == null || localIds.isEmpty()) return;
        for (Integer localId : localIds) {
            if (localId != null) touchRecord(recordType, localId, timestamp);
        }
    }

    private void markDeletedRecords(
            @NonNull String recordType, List<Integer> localIds, long timestamp) {
        if (localIds == null || localIds.isEmpty()) return;
        for (Integer localId : localIds) {
            if (localId != null) markDeletedRecord(recordType, localId, timestamp);
        }
    }

    private void touchRecord(@NonNull String recordType, long localId, long timestamp) {
        ensureMetadataRow(recordType, localId);
        syncMetadataDao.touch(recordType, localId, timestamp);
    }

    private void markDeletedRecord(@NonNull String recordType, long localId, long timestamp) {
        ensureMetadataRow(recordType, localId);
        syncMetadataDao.markDeleted(recordType, localId, timestamp);
    }

    private void ensureMetadataRow(@NonNull String recordType, long localId) {
        if (syncMetadataDao.exists(recordType, localId)) return;
        syncMetadataDao.insertIfAbsent(
                new SyncMetadataEntity(
                        recordType, localId, stableIdGenerator.nextStableId(), 0L, null));
    }

    private long resolveBatchTimestamp(@NonNull String recordType, @NonNull List<Long> localIds) {
        long now = timeProvider.now();
        if (!isLegacyImportBatch(recordType, localIds)) return now;
        synchronized (legacyImportLock) {
            if (legacyImportTimestamp > 0L && now <= legacyImportExpiresAt) {
                legacyImportExpiresAt = now + LEGACY_IMPORT_WINDOW_MILLIS;
                return legacyImportTimestamp;
            }
            legacyImportTimestamp = now;
            legacyImportExpiresAt = now + LEGACY_IMPORT_WINDOW_MILLIS;
            return legacyImportTimestamp;
        }
    }

    private boolean isLegacyImportBatch(@NonNull String recordType, @NonNull List<Long> localIds) {
        for (Long localId : localIds) {
            if (localId == null || localId <= 0L) continue;
            if (!syncMetadataDao.exists(recordType, localId)) return true;
        }
        return false;
    }

    private static List<Long> extractNoteIds(List<Note> notes) {
        List<Long> localIds = new ArrayList<>(notes.size());
        for (Note note : notes) {
            localIds.add((long) note.getId());
        }
        return localIds;
    }

    private static List<Long> extractTagIds(List<Tag> tags) {
        List<Long> localIds = new ArrayList<>(tags.size());
        for (Tag tag : tags) {
            localIds.add(tag.getId());
        }
        return localIds;
    }

    private static int resolveIntId(int currentId, long insertedId) {
        if (currentId > 0) return currentId;
        return (int) insertedId;
    }

    private static long resolveLongId(long currentId, long insertedId) {
        return currentId > 0 ? currentId : insertedId;
    }
}
