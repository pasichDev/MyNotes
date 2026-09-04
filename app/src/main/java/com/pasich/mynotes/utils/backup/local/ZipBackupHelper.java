package com.pasich.mynotes.utils.backup.local;

import static com.pasich.mynotes.extendedEditor.attach.AttachmentStorage.ATTACHMENTS_BASE_DIR;
import static com.pasich.mynotes.utils.constants.Backup.FILE_NAME_BACKUP;
import static com.pasich.mynotes.utils.constants.Backup.FILE_NAME_BACKUP_MNBKN;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;
import com.pasich.mynotes.extendedEditor.attach.AttachmentUrl;
import com.pasich.mynotes.utils.backup.models.JsonBackup;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** New ZIP-based backup format. Structure: My_Notes_Backup.json attachments/note_<id>/file.ext */
public class ZipBackupHelper {

    private static final String TAG = "ZipBackupHelper";

    /** The only entry shape an archive may place a file under: one note folder, one file name. */
    private static final Pattern ATTACHMENT_ENTRY =
            Pattern.compile(Pattern.quote(ATTACHMENTS_BASE_DIR) + "/(note_[1-9][0-9]*)/([^/]+)");

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

    /**
     * Parses a ZIP backup, unpacking its attachments into the restore staging directory.
     *
     * <p>Nothing here touches a note's live attachment folder. The archive is untrusted input and
     * the restore has not yet decided which row id each note will get, so files are staged and
     * adopted per note once it is inserted; see {@code NoteAttachmentRelocator.adoptStaged}.
     */
    public static JsonBackup readZipBackup(Context ctx, Uri uri) throws Exception {
        File staging = AttachmentStorage.restoreStagingDir(ctx);
        // Whatever an earlier restore left behind belongs to that restore, not this one.
        deleteRecursively(staging);
        try (InputStream is = ctx.getContentResolver().openInputStream(uri);
                ZipInputStream zis = new ZipInputStream(is)) {
            JsonBackup backup = readZipBackup(zis, staging);
            if (backup.isError()) {
                deleteRecursively(staging);
            }
            return backup;
        }
    }

    /**
     * Filesystem-only core: reads the archive into {@code stagingRoot}, which stands in for the
     * {@code attachments} directory an archive entry names.
     */
    @NonNull
    static JsonBackup readZipBackup(@NonNull ZipInputStream zis, @NonNull File stagingRoot)
            throws IOException {
        JsonBackup backup = null;
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (entry.getName().equals(FILE_NAME_BACKUP)) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] tmp = new byte[4096];
                int n;
                while ((n = zis.read(tmp)) != -1) {
                    buffer.write(tmp, 0, n);
                }
                try {
                    backup = new Gson().fromJson(buffer.toString("UTF-8"), JsonBackup.class);
                } catch (RuntimeException malformed) {
                    Log.w(TAG, "The backup JSON could not be read", malformed);
                    backup = null;
                }
            } else if (entry.getName().startsWith(ATTACHMENTS_BASE_DIR)) {
                // A backup file is untrusted input: it can be edited, or come from somewhere else
                // entirely. Only "attachments/note_<id>/<file>" is a place a restored note can
                // reference; anything else — a traversal, a nested path, a folder the cleaner
                // would never look in — is skipped rather than written somewhere and forgotten.
                File out = stagedAttachmentTarget(stagingRoot, entry.getName());
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
        return backup != null ? backup : new JsonBackup().error();
    }

    /**
     * Resolves one archive entry inside the staging directory, or {@code null} if it does not name
     * exactly one file in one note folder.
     *
     * @param entryName the raw name from the archive, which is attacker-controlled.
     */
    @Nullable
    static File stagedAttachmentTarget(@NonNull File stagingRoot, @NonNull String entryName)
            throws IOException {
        java.util.regex.Matcher matcher = ATTACHMENT_ENTRY.matcher(entryName);
        if (!matcher.matches() || !AttachmentUrl.isSafeSegment(matcher.group(2))) {
            return null;
        }
        File root = stagingRoot.getCanonicalFile();
        File resolved =
                new File(new File(root, matcher.group(1)), matcher.group(2)).getCanonicalFile();
        // The pattern already forbids traversal; this is the second of two independent guards.
        String prefix = root.getPath() + File.separator;
        return resolved.getPath().startsWith(prefix) ? resolved : null;
    }

    private static void deleteRecursively(@Nullable File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            Log.w(TAG, "Could not remove " + file.getName());
        }
    }
}
