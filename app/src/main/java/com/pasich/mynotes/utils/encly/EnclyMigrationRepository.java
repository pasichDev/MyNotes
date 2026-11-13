package com.pasich.mynotes.utils.encly;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.entities.SyncMetadataEntity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import com.pasich.mynotes.data.sync.SyncMutationCoordinator;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;
import com.pasich.mynotes.utils.editor.NoteViewStateStore;
import com.pasich.mynotes.utils.reminder.ReminderManager;
import com.pasich.mynotes.utils.reminder.TaskReminderManager;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Reads the library for the Encly hand-off, and clears it afterwards when the user asks. */
@Singleton
public class EnclyMigrationRepository {

    private final Context context;
    private final AppDatabase database;
    private final SyncMutationCoordinator syncMutationCoordinator;

    @Inject
    public EnclyMigrationRepository(
            @ApplicationContext Context context,
            AppDatabase database,
            SyncMutationCoordinator syncMutationCoordinator) {
        this.context = context;
        this.database = database;
        this.syncMutationCoordinator = syncMutationCoordinator;
    }

    /** Everything the hand-off would carry, read in one consistent snapshot. */
    public static final class Library {
        public final List<Note> notes;
        public final List<Tag> tags;
        public final List<Task> tasks;
        public final List<TaskCategory> categories;

        Library(List<Note> notes, List<Tag> tags, List<Task> tasks, List<TaskCategory> categories) {
            this.notes = notes;
            this.tags = tags;
            this.tasks = tasks;
            this.categories = categories;
        }

        @NonNull
        public HandoffPayloadBuilder.Summary summary() {
            return HandoffPayloadBuilder.Summary.of(notes, tags, tasks);
        }
    }

    @WorkerThread
    @NonNull
    public Library load() {
        return database.runInTransaction(() ->
                new Library(
                        database.noteDao().getAllNotesSync(),
                        database.tagsDao().getUserTagsSync(),
                        database.taskDao().getAllTasksSync(),
                        database.taskCategoryDao().getCategoriesSync()));
    }

    /** Writes the hand-off ZIP into the cache and returns it. */
    @WorkerThread
    @NonNull
    public File writeArchive(@NonNull Library library) throws IOException {
        Map<String, String> stableIds = new HashMap<>();
        for (SyncMetadataEntity row : database.syncMetadataDao().getAll()) {
            stableIds.put(key(row.recordType, row.localId), row.stableId);
        }
        HandoffPayloadBuilder payload =
                new HandoffPayloadBuilder(
                        library.notes,
                        library.tags,
                        library.tasks,
                        library.categories,
                        (recordType, localId) -> stableIds.get(key(recordType, localId)),
                        System.currentTimeMillis());
        return HandoffArchive.write(context.getCacheDir(), payload);
    }

    /** Deletes every hand-off file left in the cache. */
    public void clearArchives() {
        HandoffArchive.clear(context.getCacheDir());
    }

    /**
     * Keeps what a hand-off carried until Encly answers; see {@link ExportedSnapshotStore}.
     *
     * @return the token that reads it back.
     */
    @WorkerThread
    @NonNull
    public String saveExported(@NonNull Exported exported) throws IOException {
        return ExportedSnapshotStore.save(exportedDir(), exported);
    }

    /** What the hand-off saved under {@code token} carried, or null when it is gone. */
    @WorkerThread
    @Nullable
    public Exported loadExported(@Nullable String token) {
        return ExportedSnapshotStore.load(exportedDir(), token);
    }

    /** Deletes the hand-off snapshots saved before {@code requestedAt}. */
    @WorkerThread
    public void forgetExported(long requestedAt) {
        ExportedSnapshotStore.sweep(exportedDir(), requestedAt);
    }

    private File exportedDir() {
        return new File(context.getNoBackupFilesDir(), "encly-handoff");
    }

    /**
     * Which records a hand-off carried, and what each held when it was carried.
     *
     * <p>The clear offered afterwards removes exactly these. It used to remove whatever the
     * database held when the button was pressed, including a note written, or synced in, after the
     * archive was made, which never reached Encly. A carried record changed since then (edited
     * here, or by a sync) stays too: Encly only has its older content. Each id has the fingerprint
     * of its content at the same index.
     */
    public static final class Exported {
        public final long[] notes;
        public final long[] tasks;
        public final long[] tags;
        public final long[] categories;
        public final long[] noteFingerprints;
        public final long[] taskFingerprints;
        public final long[] tagFingerprints;
        public final long[] categoryFingerprints;

        public Exported(
                long[] notes,
                long[] tasks,
                long[] tags,
                long[] categories,
                long[] noteFingerprints,
                long[] taskFingerprints,
                long[] tagFingerprints,
                long[] categoryFingerprints) {
            this.notes = notes;
            this.tasks = tasks;
            this.tags = tags;
            this.categories = categories;
            this.noteFingerprints = noteFingerprints;
            this.taskFingerprints = taskFingerprints;
            this.tagFingerprints = tagFingerprints;
            this.categoryFingerprints = categoryFingerprints;
        }

        @NonNull
        public static Exported of(@NonNull Library library) {
            return new Exported(
                    longs(library.notes, Note::getId),
                    longs(library.tasks, Task::getId),
                    longs(library.tags, Tag::getId),
                    longs(library.categories, TaskCategory::getId),
                    longs(library.notes, EnclyMigrationRepository::fingerprint),
                    longs(library.tasks, EnclyMigrationRepository::fingerprint),
                    longs(library.tags, EnclyMigrationRepository::fingerprint),
                    longs(library.categories, EnclyMigrationRepository::fingerprint));
        }

        private static <T> long[] longs(List<T> items, ToLongFunction<T> value) {
            if (items == null) return new long[0];
            long[] out = new long[items.size()];
            for (int i = 0; i < out.length; i++) out[i] = value.applyAsLong(items.get(i));
            return out;
        }
    }

    /**
     * The items of {@code current} whose id is among {@code exported} and whose content still has
     * the fingerprint it had then ({@code fingerprints}, at the same index as the id).
     */
    @NonNull
    static <T> List<T> onlyExportedUnchanged(
            @Nullable List<T> current,
            @NonNull long[] exported,
            @NonNull long[] fingerprints,
            @NonNull ToLongFunction<T> id,
            @NonNull ToLongFunction<T> fingerprint) {
        List<T> out = new ArrayList<>();
        if (current == null || exported.length == 0 || exported.length != fingerprints.length) {
            return out;
        }
        Map<Long, Long> wanted = new HashMap<>();
        for (int i = 0; i < exported.length; i++) wanted.put(exported[i], fingerprints[i]);
        for (T item : current) {
            Long carried = wanted.get(id.applyAsLong(item));
            if (carried != null && carried == fingerprint.applyAsLong(item)) out.add(item);
        }
        return out;
    }

    /** A note's content as the hand-off carries it. */
    static long fingerprint(@NonNull Note note) {
        return fingerprintOf(
                note.getTitle(),
                note.getValue(),
                note.getValueJson(),
                note.getTag(),
                note.getAttachments(),
                String.valueOf(note.isTrash()),
                String.valueOf(note.isPinned()));
    }

    /** A task's content as the hand-off carries it; positions are left out, others shift them. */
    static long fingerprint(@NonNull Task task) {
        return fingerprintOf(
                task.getTitle(),
                task.getDescription(),
                String.valueOf(task.isDone()),
                String.valueOf(task.getCategoryId()),
                String.valueOf(task.getCreatedAt()));
    }

    static long fingerprint(@NonNull Tag tag) {
        return fingerprintOf(tag.getNameTag());
    }

    static long fingerprint(@NonNull TaskCategory category) {
        return fingerprintOf(category.getName(), category.getColorHex());
    }

    /** The first 64 bits of a SHA-256 over the parts, each length-prefixed so none can blur. */
    private static long fingerprintOf(String... parts) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        for (String part : parts) {
            if (part == null) {
                digest.update((byte) 0);
                continue;
            }
            byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
            digest.update((byte) 1);
            digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
            digest.update(bytes);
        }
        return ByteBuffer.wrap(digest.digest()).getLong();
    }

    /**
     * Deletes the notes, tasks, tags and task categories the hand-off carried, with the notes'
     * attachment files. Only ever called after Encly confirmed the move and the user confirmed this
     * separate step. Anything added since the archive was made stays, and so does anything changed
     * since then: Encly never received that content.
     *
     * @return how many carried records were kept because they changed since
     */
    @WorkerThread
    public int clearExported(@NonNull Exported exported) {
        Library library = load();
        List<Note> notes =
                onlyExportedUnchanged(
                        library.notes,
                        exported.notes,
                        exported.noteFingerprints,
                        Note::getId,
                        EnclyMigrationRepository::fingerprint);
        List<Task> tasks =
                onlyExportedUnchanged(
                        library.tasks,
                        exported.tasks,
                        exported.taskFingerprints,
                        Task::getId,
                        EnclyMigrationRepository::fingerprint);
        List<Tag> tags =
                onlyExportedUnchanged(
                        library.tags,
                        exported.tags,
                        exported.tagFingerprints,
                        Tag::getId,
                        EnclyMigrationRepository::fingerprint);
        List<TaskCategory> categories =
                onlyExportedUnchanged(
                        library.categories,
                        exported.categories,
                        exported.categoryFingerprints,
                        TaskCategory::getId,
                        EnclyMigrationRepository::fingerprint);
        int changed =
                stillPresent(library.notes, exported.notes, Note::getId)
                        + stillPresent(library.tasks, exported.tasks, Task::getId)
                        + stillPresent(library.tags, exported.tags, Tag::getId)
                        + stillPresent(library.categories, exported.categories, TaskCategory::getId)
                        - notes.size()
                        - tasks.size()
                        - tags.size()
                        - categories.size();

        for (Note note : notes) {
            if (note.getReminderTime() != null) {
                ReminderManager.cancelReminder(context, note.getId());
            }
        }
        for (Task task : tasks) {
            if (task.getReminderTime() != null) {
                TaskReminderManager.cancelReminder(context, task.getId());
            }
        }
        database.runInTransaction(() -> {
            syncMutationCoordinator.deleteNotes(notes);
            for (Task task : tasks) syncMutationCoordinator.deleteTask(task);
            for (Tag tag : tags) syncMutationCoordinator.deleteTag(tag);
            for (TaskCategory category : categories)
                syncMutationCoordinator.deleteCategory(category);
        });
        NoteViewStateStore viewState = new NoteViewStateStore(context);
        File attachments = AttachmentStorage.baseDirPath(context);
        for (Note note : notes) {
            viewState.remove(note.getId());
            deleteRecursively(new File(attachments, "note_" + note.getId()));
        }
        return changed;
    }

    /** How many of {@code current} have an id among {@code exported}. */
    private static <T> int stillPresent(
            @Nullable List<T> current, @NonNull long[] exported, @NonNull ToLongFunction<T> id) {
        if (current == null || exported.length == 0) return 0;
        Set<Long> wanted = new HashSet<>();
        for (long value : exported) wanted.add(value);
        int count = 0;
        for (T item : current) if (wanted.contains(id.applyAsLong(item))) count++;
        return count;
    }

    private static String key(String recordType, long localId) {
        return recordType + ":" + localId;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
