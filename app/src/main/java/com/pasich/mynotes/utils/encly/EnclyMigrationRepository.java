package com.pasich.mynotes.utils.encly;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.entities.SyncMetadataEntity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import com.pasich.mynotes.data.sync.SyncMutationCoordinator;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;
import com.pasich.mynotes.utils.reminder.ReminderManager;
import com.pasich.mynotes.utils.reminder.TaskReminderManager;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        return database.runInTransaction(
                () ->
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
     * Deletes notes, tasks, tags, task categories and attachment files. Only ever called after
     * Encly confirmed the move and the user confirmed this separate step.
     */
    @WorkerThread
    public void clearAllData() {
        Library library = load();
        for (Note note : library.notes) {
            if (note.getReminderTime() != null) {
                ReminderManager.cancelReminder(context, note.getId());
            }
        }
        for (Task task : library.tasks) {
            if (task.getReminderTime() != null) {
                TaskReminderManager.cancelReminder(context, task.getId());
            }
        }
        syncMutationCoordinator.clearAllUserData();
        deleteRecursively(AttachmentStorage.baseDirPath(context));
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
