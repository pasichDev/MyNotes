package com.pasich.mynotes.extendedEditor.attach;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.StatFs;
import android.provider.OpenableColumns;
import android.util.Log;

import com.pasich.mynotes.R;
import com.pasich.mynotes.extendedEditor.models.EditorAttachment;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class AttachmentStorage {

    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024; // 20 MB
    public static final long MIN_FREE_SPACE = 500L * 1024 * 1024; // 500 MB
    private static final String TAG = "AttachmentStorage";
    private static final String BASE_DIR = "attachments";



    /**
     * Перевірити чи файл можна прикріпити
     */
    public static AttachmentValidationResult validateBeforeAttach(Context ctx, Uri uri) {
        long size = getFileSize(ctx, uri);

        if (size < 0) {
            return AttachmentValidationResult.error(ctx.getString(R.string.errorAttachCalculateSize));
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
     * Розмір файла по Uri
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
     * Перевірка вільного місця
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


    private static File baseDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), BASE_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File noteDir(Context ctx, int noteId) {
        File dir = new File(baseDir(ctx), "note_" + noteId);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Зберігаємо файл як є
     */
    public static File save(Context ctx, int noteId, String originalName, byte[] raw) {
        try {
            File folder = noteDir(ctx, noteId);
            String ext = "";

            int dot = originalName.lastIndexOf('.');
            if (dot != -1) ext = originalName.substring(dot);

            String finalName = System.currentTimeMillis() + "_" + Math.abs(originalName.hashCode()) + ext;

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
     * Читання файлу напряму
     */
    public static File read(Context ctx, EditorAttachment att) {
        try {
            return resolve(ctx, att);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Розвʼязуємо URL -> фізичний файл
     */
    public static File resolve(Context ctx, EditorAttachment att) {
        try {
            Uri uri = Uri.parse(att.url);
            List<String> seg = uri.getPathSegments();

            if (seg.size() < 2) return null;

            String folder = seg.get(0);
            String name = seg.get(1);

            return new File(new File(ctx.getFilesDir(), BASE_DIR), folder + "/" + name);

        } catch (Exception e) {
            return null;
        }
    }

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
