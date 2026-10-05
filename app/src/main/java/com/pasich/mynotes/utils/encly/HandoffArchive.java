package com.pasich.mynotes.utils.encly;

import androidx.annotation.NonNull;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The hand-off file: a ZIP in {@code cacheDir/encly-handoff} holding exactly {@code handoff.json}.
 *
 * <p>It is every note in plain text, so it lives only as long as the hand-off: it is deleted when
 * Encly answers, whatever the answer, and anything left over from a crash or a killed process is
 * swept on the next start.
 */
public final class HandoffArchive {

    private HandoffArchive() {}

    @NonNull
    public static File directory(@NonNull File cacheDir) {
        return new File(cacheDir, EnclyHandoff.CACHE_DIR);
    }

    /** Writes a fresh archive and returns it; a partial file is removed if writing fails. */
    @NonNull
    public static File write(@NonNull File cacheDir, @NonNull HandoffPayloadBuilder payload)
            throws IOException {
        File dir = directory(cacheDir);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        File file = new File(dir, "handoff-" + System.currentTimeMillis() + ".zip");
        boolean done = false;
        try (ZipOutputStream zip =
                new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            zip.putNextEntry(new ZipEntry(EnclyHandoff.ENTRY_NAME));
            Writer writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
            payload.write(writer);
            writer.flush();
            zip.closeEntry();
            done = true;
        } finally {
            if (!done) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        return file;
    }

    /** Deletes every hand-off file. Returns the number of files that could not be deleted. */
    public static int clear(@NonNull File cacheDir) {
        File dir = directory(cacheDir);
        File[] files = dir.listFiles();
        int failed = 0;
        if (files != null) {
            for (File file : files) {
                if (!file.delete() && file.exists()) failed++;
            }
        }
        return failed;
    }
}
