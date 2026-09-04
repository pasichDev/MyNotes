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
import java.util.Map;
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

    /**
     * Moves a restored note's attachments to where its final row id expects them.
     *
     * @return true when the note's attachment or block JSON was rewritten and must be stored.
     */
    interface AttachmentRelocation {
        boolean relocate(@NonNull Note note, int previousId);
    }

    /** Keeps every {@code IN (...)} clause under SQLite's bound-variable limit. */
    private static final int QUERY_CHUNK = 500;

    private final TransactionExecutor transactionExecutor;
    private final NoteDao noteDao;
    private final TaskDao taskDao;
    private final TagsDao tagsDao;
    private final TaskCategoryDao taskCategoryDao;
    private final Transactions transactions;
    private final SyncMetadataDao syncMetadataDao;
    private final TimeProvider timeProvider;
    private final StableIdGenerator stableIdGenerator;
    private final AttachmentRelocation attachmentRelocation;
    private final Object legacyImportLock = new Object();
    private long legacyImportTimestamp = -1L;
    private long legacyImportExpiresAt = -1L;

    @Inject
    public SyncMutationCoordinator(
            @dagger.hilt.android.qualifiers.ApplicationContext @NonNull
                    android.content.Context context,
            @NonNull AppDatabase database) {
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
                SyncMetadata::newStableId,
                (note, previousId) -> {
                    com.pasich.mynotes.extendedEditor.attach.NoteAttachmentRelocator.Result moved =
                            com.pasich.mynotes.extendedEditor.attach.NoteAttachmentRelocator
                                    .adoptStaged(
                                            com.pasich.mynotes.extendedEditor.attach
                                                    .AttachmentStorage.restoreStagingDir(context),
                                            com.pasich.mynotes.extendedEditor.attach
                                                    .AttachmentStorage.baseDirPath(context),
                                            previousId,
                                            note.getId(),
                                            note.getAttachments(),
                                            note.getValueJson());
                    if (moved.changed) {
                        note.setAttachments(moved.attachmentsJson);
                        note.setValueJson(moved.valueJson);
                    }
                    return moved.changed;
                });
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
        this(
                transactionExecutor,
                noteDao,
                taskDao,
                tagsDao,
                taskCategoryDao,
                transactions,
                syncMetadataDao,
                timeProvider,
                stableIdGenerator,
                (note, previousId) -> false);
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
            @NonNull StableIdGenerator stableIdGenerator,
            @NonNull AttachmentRelocation attachmentRelocation) {
        this.attachmentRelocation = attachmentRelocation;
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

    public void insertTags(List<Tag> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        transactionExecutor.run(
                () -> {
                    List<Tag> tags = withoutTagsAlreadyPresent(incoming);
                    if (tags.isEmpty()) return null;
                    long timestamp =
                            resolveBatchTimestamp(
                                    SyncMetadata.RECORD_TYPE_TAG, extractTagIds(tags));

                    // Same REPLACE-insert collision as notes; see insertNotes.
                    Set<Long> taken = new LinkedHashSet<>();
                    for (Tag existing : tagsByIds(extractTagIds(tags))) {
                        taken.add(existing.getId());
                    }
                    List<Tag> keepingId = new ArrayList<>();
                    List<Tag> reassigned = new ArrayList<>();
                    Set<Long> claimed = new LinkedHashSet<>();
                    for (Tag tag : tags) {
                        if (tag.getId() > 0
                                && (taken.contains(tag.getId()) || !claimed.add(tag.getId()))) {
                            tag.id = 0;
                            reassigned.add(tag);
                        } else {
                            keepingId.add(tag);
                        }
                    }
                    assignInsertedTagIds(keepingId, timestamp);
                    assignInsertedTagIds(reassigned, timestamp);
                    return null;
                });
    }

    /** Inserts one group of tags and settles each tag's final id and metadata. */
    private void assignInsertedTagIds(@NonNull List<Tag> tags, long timestamp) {
        if (tags.isEmpty()) return;
        long[] insertedIds = tagsDao.addTags(tags);
        for (int i = 0; i < tags.size(); i++) {
            Tag tag = tags.get(i);
            long localId = resolveLongId(tag.getId(), insertedIds[i]);
            tag.id = localId;
            touchRecord(SyncMetadata.RECORD_TYPE_TAG, localId, timestamp);
        }
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

    public void insertNotes(List<Note> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        transactionExecutor.run(
                () -> {
                    // One query for every incoming id rather than one per note per pass: a
                    // large restore ran four point lookups per note inside the transaction and
                    // sat on the restore dialog for tens of seconds doing nothing else.
                    Map<Integer, Note> existingById = notesByIds(extractNoteIds(incoming));
                    List<Note> notes = withoutNotesAlreadyPresent(incoming, existingById);
                    if (notes.isEmpty()) return null;
                    long timestamp =
                            resolveBatchTimestamp(
                                    SyncMetadata.RECORD_TYPE_NOTE, extractNoteIds(notes));

                    // Split by whether the row id is still free. Inserting the ones that keep
                    // their id first leaves the autoincrement counter past all of them, which is
                    // what stops a reassigned note being handed an id a later note in the same
                    // batch is about to claim: addNotes is a REPLACE insert, so that collision
                    // silently destroyed one of the two restored notes. An id claimed twice
                    // within the batch itself is the same collision: the second note would
                    // REPLACE the first, so only the first may keep it.
                    List<Note> keepingId = new ArrayList<>();
                    List<Note> reassigned = new ArrayList<>();
                    Map<Note, Integer> previousIds = new java.util.IdentityHashMap<>();
                    Set<Integer> claimed = new LinkedHashSet<>();
                    for (Note note : notes) {
                        previousIds.put(note, note.getId());
                        if (note.getId() > 0
                                && (existingById.containsKey(note.getId())
                                        || !claimed.add(note.getId()))) {
                            note.setId(0);
                            reassigned.add(note);
                        } else {
                            keepingId.add(note);
                        }
                    }

                    assignInsertedNoteIds(keepingId, timestamp, previousIds);
                    assignInsertedNoteIds(reassigned, timestamp, previousIds);
                    return null;
                });
    }

    /** Inserts one group and settles each note's final id, metadata and attachment folder. */
    private void assignInsertedNoteIds(
            @NonNull List<Note> notes, long timestamp, @NonNull Map<Note, Integer> previousIds) {
        if (notes.isEmpty()) return;
        long[] insertedIds = noteDao.addNotes(notes);
        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            int previous = previousIds.get(note);
            int localId = resolveIntId(note.getId(), insertedIds[i]);
            note.setId(localId);
            // Its attachments were staged under the id the archive knew; they move into the
            // folder of the id it has now, whether or not the two differ.
            if (previous > 0 && attachmentRelocation.relocate(note, previous)) {
                noteDao.updateNoteContent(
                        localId,
                        note.getTitle(),
                        note.getValue(),
                        note.getValueJson(),
                        note.getDate(),
                        note.getTag(),
                        note.getAttachments());
            }
            touchRecord(SyncMetadata.RECORD_TYPE_NOTE, localId, timestamp);
        }
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
     * Drops incoming notes that this device already holds.
     *
     * <p>Restore inserts rather than replaces, so that a backup taken on another device cannot
     * destroy an unrelated note that happens to share a row id. The cost is that restoring a backup
     * onto the library it came from would duplicate every note — and the restore dialog promises
     * the opposite. A row whose id is taken by an identical note is therefore skipped: re-restoring
     * is a no-op again, while a genuinely different note under the same id is still kept alongside
     * the existing one instead of overwriting it.
     */
    @NonNull
    private static List<Note> withoutNotesAlreadyPresent(
            @NonNull List<Note> incoming, @NonNull Map<Integer, Note> existingById) {
        List<Note> result = new ArrayList<>(incoming.size());
        for (Note note : incoming) {
            Note existing = note.getId() > 0 ? existingById.get(note.getId()) : null;
            if (existing == null || !isSameNoteContent(existing, note)) {
                result.add(note);
            }
        }
        return result;
    }

    /** The existing rows for these ids, fetched in bounded batches. */
    @NonNull
    private Map<Integer, Note> notesByIds(@NonNull List<Long> ids) {
        Map<Integer, Note> result = new java.util.HashMap<>();
        List<Integer> positive = new ArrayList<>();
        for (Long id : ids) {
            if (id != null && id > 0L) positive.add(id.intValue());
        }
        for (List<Integer> chunk : chunks(positive)) {
            for (Note note : noteDao.getNotesByIdsSync(chunk)) {
                result.put(note.getId(), note);
            }
        }
        return result;
    }

    @NonNull
    private List<Tag> tagsByIds(@NonNull List<Long> ids) {
        List<Tag> result = new ArrayList<>();
        List<Long> positive = new ArrayList<>();
        for (Long id : ids) {
            if (id != null && id > 0L) positive.add(id);
        }
        for (List<Long> chunk : chunks(positive)) {
            result.addAll(tagsDao.getTagsByIdsSync(chunk));
        }
        return result;
    }

    @NonNull
    private static <T> List<List<T>> chunks(@NonNull List<T> values) {
        List<List<T>> result = new ArrayList<>();
        for (int start = 0; start < values.size(); start += QUERY_CHUNK) {
            result.add(values.subList(start, Math.min(values.size(), start + QUERY_CHUNK)));
        }
        return result;
    }

    /** True when two rows carry the same user-visible note. */
    private static boolean isSameNoteContent(@NonNull Note existing, @NonNull Note incoming) {
        return equalText(existing.getTitle(), incoming.getTitle())
                && equalText(existing.getValue(), incoming.getValue())
                && equalText(existing.getValueJson(), incoming.getValueJson())
                && equalText(existing.getTag(), incoming.getTag())
                && equalText(existing.getAttachments(), incoming.getAttachments())
                && existing.getDate() == incoming.getDate()
                && existing.isTrash() == incoming.isTrash();
    }

    /**
     * Drops incoming tags this device already has under the same name.
     *
     * <p>A note stores its tag by name, and the table has no unique index on it, so inserting a
     * second row with an existing name shows the user the same tag twice with no way to tell them
     * apart.
     */
    @NonNull
    private List<Tag> withoutTagsAlreadyPresent(@NonNull List<Tag> incoming) {
        List<Tag> result = new ArrayList<>(incoming.size());
        Set<String> seen = new LinkedHashSet<>();
        for (Tag tag : incoming) {
            String name = tag.getNameTag();
            if (name != null && !name.isEmpty()) {
                if (!seen.add(name) || tagsDao.getTagByNameSync(name) != null) {
                    continue;
                }
            }
            result.add(tag);
        }
        return result;
    }

    private static boolean equalText(String first, String second) {
        return first == null ? second == null : first.equals(second);
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
        // INSERT OR IGNORE: a row that exists is left alone without a lookup first.
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
        List<Long> positive = new ArrayList<>();
        for (Long localId : localIds) {
            if (localId != null && localId > 0L) positive.add(localId);
        }
        Set<Long> known = new LinkedHashSet<>();
        for (List<Long> chunk : chunks(positive)) {
            known.addAll(syncMetadataDao.getExistingLocalIds(recordType, chunk));
        }
        return !known.containsAll(positive);
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
