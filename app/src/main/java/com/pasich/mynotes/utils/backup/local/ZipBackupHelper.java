package com.pasich.mynotes.utils.backup.local;

import static com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.ATTACHMENTS_BASE_DIR;
import static com.pasich.mynotes.utils.constants.Backup.FILE_NAME_BACKUP;
import static com.pasich.mynotes.utils.constants.Backup.FILE_NAME_BACKUP_MNBKN;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.google.gson.Gson;
import com.pasich.mynotes.utils.backup.models.JsonBackup;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** New ZIP-based backup format. Structure: My_Notes_Backup.json attachments/note_<id>/file.ext */
public class ZipBackupHelper {

    private static final String TAG = "ZipBackupHelper";

    /** Detect ZIP by magic header "PK" */
    public static boolean isZip(byte[] data) {
        return data.length > 2 && data[0] == 0x50 && data[1] == 0x4B;
    }

    /** Create ZIP backup */
    public static File writeZipBackup(Context ctx, JsonBackup backup) throws Exception {

        File zipFile = new File(ctx.getCacheDir(), FILE_NAME_BACKUP_MNBKN);
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));

        // Write backup.json
        zos.putNextEntry(new ZipEntry(FILE_NAME_BACKUP));
        String json = new Gson().toJson(backup);
        zos.write(json.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();

        // Write attachments (full structure)
        File baseDir = new File(ctx.getFilesDir(), ATTACHMENTS_BASE_DIR);

        if (baseDir.exists()) {
            File[] noteDirs = baseDir.listFiles();

            if (noteDirs != null) {
                for (File noteDir : noteDirs) {

                    if (!noteDir.isDirectory()) continue;
                    File[] files = noteDir.listFiles();

                    if (files == null) continue;

                    for (File attachment : files) {
                        String entryName =
                                ATTACHMENTS_BASE_DIR
                                        + "/"
                                        + noteDir.getName()
                                        + "/"
                                        + attachment.getName();

                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.write(Files.readAllBytes(attachment.toPath()));
                        zos.closeEntry();
                    }
                }
            }
        }

        zos.close();
        return zipFile;
    }

    /** Parse ZIP backup */
    public static JsonBackup readZipBackup(Context ctx, Uri uri) throws Exception {

        JsonBackup backup = null;

        try (InputStream is = ctx.getContentResolver().openInputStream(uri);
                ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {

                // ================== backup.json ==================
                if (entry.getName().equals(FILE_NAME_BACKUP)) {

                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] tmp = new byte[4096];
                    int n;

                    while ((n = zis.read(tmp)) != -1) {
                        buffer.write(tmp, 0, n);
                    }

                    String json = buffer.toString("UTF-8");
                    backup = new Gson().fromJson(json, JsonBackup.class);
                }

                // ================== attachments/... ==================
                else if (entry.getName().startsWith(ATTACHMENTS_BASE_DIR)) {

                    // A backup file is untrusted input: it can be edited, or come from
                    // somewhere else entirely. "attachments/../../databases/notes" also starts
                    // with the prefix above, so without resolving the path first an archive
                    // could write anywhere the app can write.
                    File out = safeAttachmentTarget(ctx, entry.getName());
                    if (out == null || entry.isDirectory()) {
                        Log.w(TAG, "Skipping a backup entry outside the attachment directory");
                        zis.closeEntry();
                        continue;
                    }
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        Log.w(TAG, "Could not create the attachment directory for a backup entry");
                        zis.closeEntry();
                        continue;
                    }

                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        byte[] data = new byte[4096];
                        int n;

                        while ((n = zis.read(data)) != -1) {
                            fos.write(data, 0, n);
                        }
                    }
                }

                zis.closeEntry();
            }
        }

        return backup != null ? backup : new JsonBackup().error();
    }

    /**
     * Resolves one archive entry inside the attachment directory, or {@code null} if it escapes.
     *
     * @param entryName the raw name from the archive, which is attacker-controlled.
     */
    private static File safeAttachmentTarget(Context ctx, String entryName) throws IOException {
        File root = new File(ctx.getFilesDir(), ATTACHMENTS_BASE_DIR).getCanonicalFile();
        File resolved = new File(ctx.getFilesDir(), entryName).getCanonicalFile();
        String prefix = root.getPath() + File.separator;
        return resolved.getPath().startsWith(prefix) ? resolved : null;
    }
}
