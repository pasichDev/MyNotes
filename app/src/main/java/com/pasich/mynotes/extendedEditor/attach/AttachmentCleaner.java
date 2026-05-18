package com.pasich.mynotes.extendedEditor.attach;

import static com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.ATTACHMENTS_BASE_DIR;

import android.content.Context;
import android.util.Log;
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
 * Utility class responsible for maintaining consistency of attachment files.
 *
 * <p>This cleaner keeps the filesystem in sync with the note's attachments JSON: - Parses the
 * note's attachment metadata. - Resolves actual file paths inside internal storage. - Deletes
 * orphaned files that are no longer referenced by the JSON.
 *
 * <p>Called after successful autosave or manual save of a note.
 */
public class AttachmentCleaner {

    private static final String TAG = "AttachmentCleaner";
    private static final Gson gson = new Gson();

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
     * Performs a cleanup of attachment files for a given note.
     *
     * <p>Logic: 1) Parses note.attachments JSON into EditorAttachment models. 2) Collects expected
     * filenames referenced by the JSON. 3) Locates the actual attachment directory:
     * /files/attachments/note_<id>. 4) Deletes all files that are not referenced (orphans).
     *
     * <p>Notes: - Runs silently in production; detailed logs appear only in debug builds. - If the
     * attachment folder does not exist, the method exits safely. - Never creates new directories —
     * cleanup must not modify the FS structure.
     *
     * @param ctx Application context.
     * @param note Source note containing attachments metadata.
     */
    public static void cleanup(Context ctx, Note note) {

        if (note == null) return;

        try {
            d("Cleanup start");

            String json = note.getAttachments();
            Type type = new TypeToken<List<EditorAttachment>>() {}.getType();
            List<EditorAttachment> list = gson.fromJson(json, type);
            if (list == null) list = new ArrayList<>();

            d("Parsed attachments: " + list.size());

            int noteId = note.getId();

            // expected files
            Set<String> expected = new HashSet<>();
            for (EditorAttachment att : list) {
                try {
                    File f = AttachmentStorage.resolve(ctx, att);
                    if (f != null) {
                        expected.add(f.getName());
                    } else {
                        w("resolve null for url=" + att.url);
                    }
                } catch (Exception ex) {
                    e("resolve error for " + att.url, ex);
                }
            }

            // folder
            File folder =
                    new File(new File(ctx.getFilesDir(), ATTACHMENTS_BASE_DIR), "note_" + noteId);
            if (!folder.exists() || !folder.isDirectory()) {
                d("No folder → nothing to clean");
                return;
            }

            File[] actualFiles = folder.listFiles();
            if (actualFiles == null) return;

            // delete orphans
            for (File f : actualFiles) {
                if (!expected.contains(f.getName())) {
                    boolean deleted = f.delete();
                    w("Orphan deleted: " + f.getName() + " → " + deleted);
                }
            }

            d("Cleanup complete");

        } catch (Exception ex) {
            e("cleanup failed", ex);
        }
    }

    public static void deleteAttachmentFolderByNoteId(Context ctx, long noteId) {
        try {
            File base = new File(ctx.getFilesDir(), ATTACHMENTS_BASE_DIR);
            File folder = new File(base, "note_" + noteId);

            if (!folder.exists()) {
                d("Folder note_" + noteId + " → not found");
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
