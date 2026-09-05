package com.pasich.mynotes.extendedEditor.attach;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.StatFs;
import android.provider.OpenableColumns;
import android.util.Log;
import com.pasich.mynotes.R;
import com.pasich.mynotes.extendedEditor.models.EditorAttachment;
import com.pasich.mynotes.utils.file.ImageOptimizer;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Utility class for managing note attachments stored in the app's internal storage. Handles
 * validation, saving, resolving, reading, and deleting attachment files.
 */
public class AttachmentStorage {

    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024; // 20 MB
    public static final long MIN_FREE_SPACE = 500L * 1024 * 1024; // 500 MB
    public static final String ATTACHMENTS_BASE_DIR = "attachments";
    private static final String TAG = "AttachmentStorage";

    /**
     * Validates whether a file can be attached to a note.
     *
     * <p>Performs: - File size calculation - Maximum size check - Free internal storage space check
     *
     * @param ctx application context
     * @param uri Uri of the selected file
     * @return validation result containing status and optional error message
     */
    public static AttachmentValidationResult validateBeforeAttach(Context ctx, Uri uri) {
        long size = getFileSize(ctx, uri);

        if (size < 0) {
            return AttachmentValidationResult.error(
                    ctx.getString(R.string.errorAttachCalculateSize));
        }

        if (size > MAX_FILE_SIZE) {
            return AttachmentValidationResult.error(ctx.getString(R.string.errorAttachLimitSize));
        }

        if (!hasEnoughSpace(ctx, MIN_FREE_SPACE)) {
            return AttachmentValidationResult.error(ctx.getString(R.string.errorAttachFreeSize));
        }

        return AttachmentValidationResult.ok();
    }

    /**
     * Retrieves file size from a content Uri using OpenableColumns.SIZE.
     *
     * @param ctx application context
     * @param uri target file Uri
     * @return file size in bytes, or -1 if not available
     */
    public static long getFileSize(Context ctx, Uri uri) {
        try (Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) return cursor.getLong(sizeIndex);
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    /**
     * Checks if internal storage has the required amount of free space.
     *
     * @param ctx application context
     * @param requiredBytes minimum required bytes
     * @return true if available space ≥ requiredBytes, false otherwise
     */
    public static boolean hasEnoughSpace(Context ctx, long requiredBytes) {
        try {
            File dir = ctx.getFilesDir();
            StatFs stat = new StatFs(dir.getAbsolutePath());
            long available = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            return available >= requiredBytes;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns (and creates if necessary) the base attachment directory:
     * /data/data/<package>/files/attachments
     */
    private static File baseDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), ATTACHMENTS_BASE_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Returns (and creates if necessary) the directory for a specific note: /attachments/note_<id>
     *
     * @param ctx application context
     * @param noteId ID of the note
     */
    public static File noteFolder(Context ctx, int noteId) {
        File dir = new File(baseDir(ctx), "note_" + noteId);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Saves a file into the note-specific folder.
     *
     * <p>Automatically generates unique filename using: timestamp + hash(originalName) + extension
     *
     * @param ctx app context
     * @param noteId target note id
     * @param originalName original filename (used for extension extraction)
     * @param raw file raw bytes
     * @return saved File object or null on failure
     */
    public static File save(
            Context ctx,
            int noteId,
            String originalName,
            byte[] raw,
            boolean isExtraOptimizeEnabled) {
        try {
            File folder = noteFolder(ctx, noteId);

            // We determine the format based on the first bytes
            ImageOptimizer.OutFormat detectedFormat = ImageOptimizer.detectFormat(raw);

            boolean isImage =
                    detectedFormat == ImageOptimizer.OutFormat.JPEG
                            || detectedFormat == ImageOptimizer.OutFormat.PNG
                            || detectedFormat == ImageOptimizer.OutFormat.WEBP;

            // If it's an image, let's optimize it
            if (isImage) {
                raw = ImageOptimizer.optimizeRawImage(raw, detectedFormat, isExtraOptimizeEnabled);
            }

            // File name generation
            String ext = "";

            int dot = originalName.lastIndexOf('.');
            if (dot != -1) ext = originalName.substring(dot);

            String finalName =
                    System.currentTimeMillis() + "_" + Math.abs(originalName.hashCode()) + ext;

            File out = new File(folder, finalName);

            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(raw);
            }

            return out;

        } catch (Exception e) {
            Log.e(TAG, "save() error", e);
            return null;
        }
    }

    /**
     * Reads an attachment file by resolving its stored internal path.
     *
     * @param ctx app context
     * @param att attachment model
     * @return File instance or null if not found
     */
    public static File read(Context ctx, EditorAttachment att) {
        try {
            return resolve(ctx, att);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves EditorAttachment.url → real File path inside internal storage.
     *
     * <p>Canonical URL format: editorjs://attachments/note_<id>/filename.ext
     *
     * <p>Legacy file://attachments/... references are still accepted.
     *
     * @param ctx app context
     * @param att attachment model
     * @return File instance or null on error
     */
    public static File resolve(Context ctx, EditorAttachment att) {
        return att == null ? null : resolve(ctx, att.url);
    }

    public static File resolve(Context ctx, String url) {
        AttachmentUrl parsed = AttachmentUrl.parse(url);
        return parsed == null ? null : parsed.resolveWithin(baseDirPath(ctx));
    }

    /** The app-private attachment root, without creating it. */
    public static File baseDirPath(Context ctx) {
        return new File(ctx.getFilesDir(), ATTACHMENTS_BASE_DIR);
    }

    /**
     * Where a backup's attachments are unpacked before any note has been inserted.
     *
     * <p>Extracting straight into {@link #baseDirPath} overwrote the files of whatever local note
     * happened to share a row id with a note in the archive, before the restore had decided whether
     * that note would even keep its id. Files wait here until the row exists, then move into the
     * folder of the id the note actually received. Under the cache directory so a restore that
     * never finishes leaves only something the system may reclaim.
     */
    public static File restoreStagingDir(Context ctx) {
        return new File(new File(ctx.getCacheDir(), "restore-staging"), ATTACHMENTS_BASE_DIR);
    }

    /**
     * Builds the canonical URL for a file this app just wrote into a note's folder.
     *
     * <p>Every producer goes through here — the editor upload path and sync restore alike — so a
     * reference can never be stored in a shape the WebView interceptor refuses to serve.
     */
    public static String urlFor(int noteId, String fileName) {
        return AttachmentUrl.canonical(noteId, fileName);
    }

    /**
     * Deletes the attachment file from internal storage.
     *
     * @param ctx app context
     * @param att attachment model
     * @return true if deletion succeeded, false otherwise
     */
    public static boolean delete(Context ctx, EditorAttachment att) {
        try {
            File file = read(ctx, att);
            if (file != null && file.exists()) {
                return file.delete();
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    public static long getTotalAttachmentsSize(Context ctx) {
        long total = 0;
        File base = new File(ctx.getFilesDir(), ATTACHMENTS_BASE_DIR);

        if (!base.exists()) return 0;

        File[] noteDirs = base.listFiles();
        if (noteDirs == null) return 0;

        for (File dir : noteDirs) {
            if (dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files == null) continue;

                for (File f : files) {
                    if (f.isFile()) total += f.length();
                }
            }
        }

        return total;
    }

    /**
     * Represents validation result for attachment checks.
     *
     * <p>Contains: - ok: validation success flag - error: user-readable error message (when ok ==
     * false)
     */
    public static class AttachmentValidationResult {

        public final boolean ok;
        public final String error;

        private AttachmentValidationResult(boolean ok, String error) {
            this.ok = ok;
            this.error = error;
        }

        public static AttachmentValidationResult ok() {
            return new AttachmentValidationResult(true, null);
        }

        public static AttachmentValidationResult error(String msg) {
            return new AttachmentValidationResult(false, msg);
        }
    }
}
