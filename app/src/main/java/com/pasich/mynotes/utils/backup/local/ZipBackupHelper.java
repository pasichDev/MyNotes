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
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** New ZIP-based backup format. Structure: My_Notes_Backup.json attachments/note_<id>/file.ext */
public class ZipBackupHelper {

    private static final String TAG = "ZipBackupHelper";

    /**
     * The most an archive may unpack to, all entries together.
     *
     * <p>An archive is untrusted input, and ZIP compresses a run of zeros a thousandfold; without a
     * ceiling a small file could fill the device. Generous for a real library, far below what a
     * bomb wants.
     */
    static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 4L * 1024L * 1024L * 1024L;

    /** The only entry shape an archive may place a file under: one note folder, one file name. */
    private static final Pattern ATTACHMENT_ENTRY =
            Pattern.compile(Pattern.quote(ATTACHMENTS_BASE_DIR) + "/(note_[1-9][0-9]*)/([^/]+)");

    /** Detect ZIP by magic header "PK" */
    public static boolean isZip(byte[] data) {
        return data.length > 2 && data[0] == 0x50 && data[1] == 0x4B;
    }

    /**
     * Detects ZIP by the magic header "PK", reading only the first bytes of the stream.
     *
     * <p>The stream is left part-read; the caller opens the document again to parse it.
     */
    public static boolean isZip(@NonNull InputStream in) throws IOException {
        int first = in.read();
        int second = first == -1 ? -1 : in.read();
        return first == 0x50 && second == 0x4B;
    }

    /**
     * Creates the ZIP backup in the cache directory.
     *
     * <p>Everything is streamed — the JSON straight from Gson, each attachment in chunks — so the
     * size of the library, not of the heap, is the limit.
     */
    public static File writeZipBackup(Context ctx, JsonBackup backup) throws IOException {
        File zipFile = new File(ctx.getCacheDir(), FILE_NAME_BACKUP_MNBKN);
        try {
            writeZipBackup(backup, new File(ctx.getFilesDir(), ATTACHMENTS_BASE_DIR), zipFile);
        } catch (IOException | RuntimeException e) {
            if (zipFile.exists() && !zipFile.delete()) {
                Log.w(TAG, "Could not remove a partly written backup archive");
            }
            throw e;
        }
        return zipFile;
    }

    /** Filesystem-only core of {@link #writeZipBackup(Context, JsonBackup)}. */
    static void writeZipBackup(
            @NonNull JsonBackup backup, @NonNull File attachmentsDir, @NonNull File zipFile)
            throws IOException {
        try (ZipOutputStream zos =
                new ZipOutputStream(
                        new BufferedOutputStream(new FileOutputStream(zipFile), 64 * 1024))) {

            // Write backup.json
            zos.putNextEntry(new ZipEntry(FILE_NAME_BACKUP));
            Writer writer = new OutputStreamWriter(zos, StandardCharsets.UTF_8);
            new Gson().toJson(backup, writer);
            // Flushed, not closed: closing the writer would close the archive.
            writer.flush();
            zos.closeEntry();

            // Write attachments (full structure)
            File[] noteDirs = attachmentsDir.listFiles();
            if (noteDirs == null) return;

            byte[] chunk = new byte[64 * 1024];
            for (File noteDir : noteDirs) {
                if (!noteDir.isDirectory()) continue;
                File[] files = noteDir.listFiles();
                if (files == null) continue;

                for (File attachment : files) {
                    if (!attachment.isFile()) continue;
                    String entryName =
                            ATTACHMENTS_BASE_DIR
                                    + "/"
                                    + noteDir.getName()
                                    + "/"
                                    + attachment.getName();

                    zos.putNextEntry(new ZipEntry(entryName));
                    try (InputStream in = new FileInputStream(attachment)) {
                        int n;
                        while ((n = in.read(chunk)) != -1) {
                            zos.write(chunk, 0, n);
                        }
                    }
                    zos.closeEntry();
                }
            }
        }
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
        return readZipBackup(zis, stagingRoot, MAX_TOTAL_UNCOMPRESSED_BYTES);
    }

    @NonNull
    static JsonBackup readZipBackup(
            @NonNull ZipInputStream zis, @NonNull File stagingRoot, long maxTotalBytes)
            throws IOException {
        JsonBackup backup = null;
        long[] total = {0L};
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (entry.getName().equals(FILE_NAME_BACKUP)) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] tmp = new byte[4096];
                int n;
                while ((n = zis.read(tmp)) != -1) {
                    if (!account(total, n, maxTotalBytes)) {
                        return new JsonBackup().error();
                    }
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
                        if (!account(total, n, maxTotalBytes)) {
                            Log.w(TAG, "The backup unpacks to more than the restore allows");
                            return new JsonBackup().error();
                        }
                        fos.write(data, 0, n);
                    }
                }
            }
            zis.closeEntry();
        }
        return backup != null ? backup : new JsonBackup().error();
    }

    /** Adds to the running total; false once the ceiling is passed. */
    private static boolean account(long[] total, int read, long maxTotalBytes) {
        total[0] += read;
        return total[0] <= maxTotalBytes;
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
