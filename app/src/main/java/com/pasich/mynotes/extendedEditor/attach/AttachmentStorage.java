package com.pasich.mynotes.extendedEditor.attach;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.pasich.mynotes.extendedEditor.models.EditorAttachment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.List;

public class AttachmentStorage {

    private static final String TAG = "AttachmentStorage";
    private static final String BASE_DIR = "attachments";
    private static final String TEMP_DIR = "attachments_temp";

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

    public static File save(Context ctx, int noteId, String originalName, byte[] raw) {
        try {
            File folder = noteDir(ctx, noteId);
            String ext = "";

            int dot = originalName.lastIndexOf('.');
            if (dot != -1) ext = originalName.substring(dot);

            // Унікальна назва
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

    public static byte[] read(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (Exception e) {
            return null;
        }
    }

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

    public static File copyTemp(Context ctx, EditorAttachment att) {
        try {
            File original = resolve(ctx, att);
            if (original == null) return null;

            File tempDir = new File(ctx.getCacheDir(), TEMP_DIR);
            if (!tempDir.exists()) tempDir.mkdirs();

            File out = new File(tempDir, original.getName());

            try (FileInputStream in = new FileInputStream(original);
                 FileOutputStream outStream = new FileOutputStream(out)) {

                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    outStream.write(buffer, 0, len);
                }
            }

            return out;
        } catch (Exception e) {
            return null;
        }
    }


    public static void cleanupTemp(Context ctx) {
        File tempDir = new File(ctx.getCacheDir(), TEMP_DIR);

        if (tempDir.exists() && tempDir.isDirectory()) {
            File[] files = tempDir.listFiles();
            if (files != null) for (File f : files) f.delete();
        }
    }
}
