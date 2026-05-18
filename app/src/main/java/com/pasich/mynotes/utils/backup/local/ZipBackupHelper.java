package com.pasich.mynotes.utils.backup.local;

import static com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.ATTACHMENTS_BASE_DIR;
import static com.pasich.mynotes.utils.constants.Backup.FILE_NAME_BACKUP;
import static com.pasich.mynotes.utils.constants.Backup.FILE_NAME_BACKUP_MNBKN;

import android.content.Context;
import android.net.Uri;
import com.google.gson.Gson;
import com.pasich.mynotes.utils.backup.models.JsonBackup;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** New ZIP-based backup format. Structure: My_Notes_Backup.json attachments/note_<id>/file.ext */
public class ZipBackupHelper {

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

                    File out = new File(ctx.getFilesDir(), entry.getName());
                    File parent = out.getParentFile();
                    assert parent != null;
                    if (!parent.exists()) parent.mkdirs();

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
}
