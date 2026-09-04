package com.pasich.mynotes.extendedEditor.attach;

import static com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.ATTACHMENTS_BASE_DIR;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.pasich.mynotes.BuildConfig;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.extendedEditor.models.EditorAttachment;
import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps a note's attachment folder consistent with its attachments JSON.
 *
 * <p>The one rule that matters here: a reference this class cannot parse is <em>unknown</em>, never
 * <em>absent</em>. Treating an unresolvable reference as an orphan is what turned a URL-scheme
 * mismatch into the deletion of every attachment a user owned, so an unresolved reference now
 * aborts the whole pass and leaves the folder untouched. Leaving a genuine orphan on disk costs
 * bytes; deleting a referenced file costs the file.
 *
 * <p>Called after a successful autosave or manual save of a note.
 */
public class AttachmentCleaner {

    private static final String TAG = "AttachmentCleaner";
    private static final Gson gson = new Gson();

    /** Outcome of one cleanup pass; {@code ABORTED_*} guarantees nothing was deleted. */
    public enum Result {
        /** Orphans were considered and any that existed were removed. */
        CLEANED,
        /** No attachment folder for this note; nothing to do. */
        NO_FOLDER,
        /** The attachments JSON could not be parsed. Nothing was deleted. */
        ABORTED_UNREADABLE_METADATA,
        /** At least one reference could not be resolved safely. Nothing was deleted. */
        ABORTED_UNRESOLVED_REFERENCE
    }

    private static void d(String msg) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg);
    }

    private static void w(String msg) {
        if (BuildConfig.DEBUG) Log.w(TAG, msg);
    }

    private static void e(String msg, Throwable t) {
        if (BuildConfig.DEBUG) Log.e(TAG, msg, t);
    }

    /**
     * Removes files in {@code note_<id>} that the note's JSON no longer references.
     *
     * @param ctx Application context.
     * @param note Source note carrying the attachments metadata.
     */
    public static Result cleanup(Context ctx, Note note) {
        if (note == null || ctx == null) return Result.ABORTED_UNREADABLE_METADATA;
        return cleanup(
                new File(ctx.getFilesDir(), ATTACHMENTS_BASE_DIR),
                note.getId(),
                note.getAttachments());
    }

    /**
     * Filesystem-only core, so the abort rules are exercised by ordinary JVM unit tests.
     *
     * @param attachmentsRoot the app-private attachment root.
     * @param noteId the note whose folder is being cleaned.
     * @param attachmentsJson the note's serialized attachment list.
     */
    @NonNull
    static Result cleanup(
            @NonNull File attachmentsRoot, int noteId, @Nullable String attachmentsJson) {
        List<EditorAttachment> referenced;
        try {
            Type type = new TypeToken<List<EditorAttachment>>() {}.getType();
            referenced = gson.fromJson(attachmentsJson, type);
        } catch (RuntimeException error) {
            // Unparseable metadata says nothing about which files are still needed.
            e("Attachments JSON is unreadable; skipping cleanup", error);
            return Result.ABORTED_UNREADABLE_METADATA;
        }
        if (referenced == null) referenced = new ArrayList<>();

        Set<String> expected = new HashSet<>();
        for (EditorAttachment attachment : referenced) {
            if (attachment == null) {
                w("Null attachment entry; skipping cleanup");
                return Result.ABORTED_UNRESOLVED_REFERENCE;
            }
            AttachmentUrl parsed = AttachmentUrl.parse(attachment.url);
            if (parsed == null) {
                w("Unresolvable attachment reference; skipping cleanup");
                return Result.ABORTED_UNRESOLVED_REFERENCE;
            }
            if (parsed.resolveWithin(attachmentsRoot) == null) {
                w("Attachment reference escapes the attachment root; skipping cleanup");
                return Result.ABORTED_UNRESOLVED_REFERENCE;
            }
            expected.add(parsed.getFileName());
        }

        File folder = new File(attachmentsRoot, "note_" + noteId);
        if (!folder.isDirectory()) {
            d("No folder for note_" + noteId);
            return Result.NO_FOLDER;
        }
        File[] actualFiles = folder.listFiles();
        if (actualFiles == null) {
            // A directory that will not list is a filesystem fault, not an empty directory.
            w("Attachment folder could not be listed; skipping cleanup");
            return Result.ABORTED_UNRESOLVED_REFERENCE;
        }

        for (File candidate : actualFiles) {
            if (!candidate.isFile() || expected.contains(candidate.getName())) continue;
            boolean deleted = candidate.delete();
            w("Orphan deleted: " + candidate.getName() + " -> " + deleted);
        }
        return Result.CLEANED;
    }

    public static void deleteAttachmentFolderByNoteId(Context ctx, long noteId) {
        try {
            File base = new File(ctx.getFilesDir(), ATTACHMENTS_BASE_DIR);
            File folder = new File(base, "note_" + noteId);

            if (!folder.exists()) {
                d("Folder note_" + noteId + " -> not found");
                return;
            }

            boolean result = deleteRecursively(folder);

            d("Deleted folder note_" + noteId + ": " + result);

        } catch (Exception ex) {
            e("deleteAttachmentFolderByNoteId failed", ex);
        }
    }

    private static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return false;

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursively(child);
                }
            }
        }

        return file.delete();
    }
}
