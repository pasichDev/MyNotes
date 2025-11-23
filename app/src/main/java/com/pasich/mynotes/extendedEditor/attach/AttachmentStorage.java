package com.pasich.mynotes.extendedEditor.attach;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.pasich.mynotes.extendedEditor.models.EditorAttachment;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class AttachmentStorage {

    private static final String TAG = "AttachmentStorage";
    private static final String BASE_DIR = "attachments";

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

    /** Зберігаємо файл як є */
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

    /** Читання файлу напряму */
    public static File read(Context ctx, EditorAttachment att) {
        try {
            return resolve(ctx, att);   // повертає реальний файл
        } catch (Exception e) {
            return null;
        }
    }

    /** Розвʼязуємо URL -> фізичний файл */
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

}
