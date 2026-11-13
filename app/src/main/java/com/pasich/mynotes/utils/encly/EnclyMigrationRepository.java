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
     * Which records a hand-off carried.
     *
     * <p>The clear offered afterwards removes exactly these. It used to remove whatever the
     * database held when the button was pressed, including a note written, or synced in, after the
     * archive was made — which never reached Encly.
     */
    public static final class Exported {
        public final long[] notes;
        public final long[] tasks;
        public final long[] tags;
        public final long[] categories;

        public Exported(long[] notes, long[] tasks, long[] tags, long[] categories) {
            this.notes = notes;
            this.tasks = tasks;
            this.tags = tags;
            this.categories = categories;
        }

        @NonNull
        public static Exported of(@NonNull Library library) {
            return new Exported(
                    ids(library.notes, Note::getId),
                    ids(library.tasks, Task::getId),
                    ids(library.tags, Tag::getId),
                    ids(library.categories, TaskCategory::getId));
        }

        private static <T> long[] ids(List<T> items, ToLongFunction<T> id) {
            if (items == null) return new long[0];
            long[] out = new long[items.size()];
            for (int i = 0; i < out.length; i++) out[i] = id.applyAsLong(items.get(i));
            return out;
        }
    }

    /** The items of {@code current} whose id is among {@code exported}. */
    @NonNull
    static <T> List<T> onlyExported(
            @Nullable List<T> current, @NonNull long[] exported, @NonNull ToLongFunction<T> id) {
        List<T> out = new ArrayList<>();
        if (current == null || exported.length == 0) return out;
        Set<Long> wanted = new HashSet<>();
        for (long value : exported) wanted.add(value);
        for (T item : current) {
            if (wanted.contains(id.applyAsLong(item))) out.add(item);
        }
        return out;
    }

    /**
     * Deletes the notes, tasks, tags and task categories the hand-off carried, with the notes'
     * attachment files. Only ever called after Encly confirmed the move and the user confirmed this
     * separate step. Anything added since the archive was made stays.
     */
    @WorkerThread
    public void clearExported(@NonNull Exported exported) {
        Library library = load();
        List<Note> notes = onlyExported(library.notes, exported.notes, Note::getId);
        List<Task> tasks = onlyExported(library.tasks, exported.tasks, Task::getId);
        List<Tag> tags = onlyExported(library.tags, exported.tags, Tag::getId);
        List<TaskCategory> categories =
                onlyExported(library.categories, exported.categories, TaskCategory::getId);

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
