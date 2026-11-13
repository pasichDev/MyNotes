package com.pasich.mynotes.utils.encly;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;

/**
 * Keeps the record of what a hand-off carried ({@link EnclyMigrationRepository.Exported}) on disk
 * while the Meet Encly screen waits for Encly's answer.
 *
 * <p>It has to outlive the screen, and the process, while Encly is in front. It used to go into the
 * saved instance state, but that is an id and a fingerprint for every record in the library: a big
 * library overflowed the Binder transaction and crashed the app on the way to the background. Only
 * the token naming the file goes into the saved state now.
 *
 * <p>A token is the time it was made and a random part. A sweep deletes only files made before it
 * was asked for, so a sweep that runs late cannot take a snapshot saved after it.
 */
public final class ExportedSnapshotStore {

    private static final String PREFIX = "exported-";
    private static final String SUFFIX = ".bin";
    private static final int FORMAT = 1;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ExportedSnapshotStore() {}

    /**
     * Writes the snapshot into {@code dir}, replacing any saved before it.
     *
     * @return the token that reads it back.
     */
    @NonNull
    public static synchronized String save(
            @NonNull File dir, @NonNull EnclyMigrationRepository.Exported exported)
            throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        String token = System.currentTimeMillis() + "-" + Long.toHexString(RANDOM.nextLong() >>> 1);
        File file = fileFor(dir, token);
        File temp = new File(dir, PREFIX + token + ".tmp");
        try (DataOutputStream out =
                new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))) {
            out.writeInt(FORMAT);
            writeLongs(out, exported.notes);
            writeLongs(out, exported.tasks);
            writeLongs(out, exported.tags);
            writeLongs(out, exported.categories);
            writeLongs(out, exported.noteFingerprints);
            writeLongs(out, exported.taskFingerprints);
            writeLongs(out, exported.tagFingerprints);
            writeLongs(out, exported.categoryFingerprints);
        } catch (IOException e) {
            temp.delete();
            throw e;
        }
        if (!temp.renameTo(file)) {
            temp.delete();
            throw new IOException("Cannot store " + file);
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File other : files) {
                if (other.getName().startsWith(PREFIX) && !other.equals(file)) other.delete();
            }
        }
        return token;
    }

    /** The snapshot saved under {@code token}, or null when it is gone or unreadable. */
    @Nullable
    public static synchronized EnclyMigrationRepository.Exported load(
            @NonNull File dir, @Nullable String token) {
        if (token == null || !isToken(token)) return null;
        File file = fileFor(dir, token);
        if (!file.isFile()) return null;
        try (DataInputStream in =
                new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != FORMAT) return null;
            long max = file.length() / Long.BYTES;
            long[] notes = readLongs(in, max);
            long[] tasks = readLongs(in, max);
            long[] tags = readLongs(in, max);
            long[] categories = readLongs(in, max);
            long[] notePrints = readLongs(in, max);
            long[] taskPrints = readLongs(in, max);
            long[] tagPrints = readLongs(in, max);
            long[] categoryPrints = readLongs(in, max);
            // Without a fingerprint per id nothing could tell a record changed since the
            // hand-off, so a damaged file offers no clear at all.
            if (notes.length != notePrints.length
                    || tasks.length != taskPrints.length
                    || tags.length != tagPrints.length
                    || categories.length != categoryPrints.length) {
                return null;
            }
            return new EnclyMigrationRepository.Exported(
                    notes,
                    tasks,
                    tags,
                    categories,
                    notePrints,
                    taskPrints,
                    tagPrints,
                    categoryPrints);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Deletes the snapshots saved before {@code requestedAt}, in milliseconds since the epoch; pass
     * {@link Long#MAX_VALUE} to delete every one.
     *
     * @return how many were deleted.
     */
    public static synchronized int sweep(@NonNull File dir, long requestedAt) {
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int deleted = 0;
        for (File file : files) {
            String name = file.getName();
            if (!name.startsWith(PREFIX)) continue;
            if (madeAt(name) < requestedAt && file.delete()) deleted++;
        }
        return deleted;
    }

    private static File fileFor(File dir, String token) {
        return new File(dir, PREFIX + token + SUFFIX);
    }

    private static boolean isToken(String token) {
        return token.matches("\\d+-[0-9a-f]+");
    }

    /** When the file was made, from its name; a name that does not say counts as long ago. */
    private static long madeAt(String name) {
        int start = PREFIX.length();
        int end = name.indexOf('-', start);
        if (end < 0) return Long.MIN_VALUE;
        try {
            return Long.parseLong(name.substring(start, end));
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }

    private static void writeLongs(DataOutputStream out, long[] values) throws IOException {
        out.writeInt(values.length);
        for (long value : values) out.writeLong(value);
    }

    private static long[] readLongs(DataInputStream in, long max) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > max) throw new IOException("Bad length " + length);
        long[] values = new long[length];
        for (int i = 0; i < length; i++) values[i] = in.readLong();
        return values;
    }
}
