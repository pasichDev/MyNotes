package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers the hash of an attachment file until the file changes.
 *
 * <p>Every snapshot build used to read and digest every attachment in the library, and a sync
 * builds at least once — twice when the first-sync estimate precedes it — so an idle sync of a
 * library with gigabytes of attachments was dominated by re-hashing bytes nothing had touched. A
 * file is keyed by its path, size and modification time, the same test a version control index
 * uses; a file rewritten in place with identical size within the same millisecond is the one case
 * this cannot see, and no writer in this app does that.
 *
 * <p>Persisted as JSON next to the download cache so the saving survives the store instance, which
 * lives only as long as one sync. Loading, saving and every lookup is best effort: a lost or
 * unreadable cache costs one full re-hash, never correctness.
 */
final class AttachmentHashCache {

    /** Produces the hash when the cache has no answer. */
    interface Hasher {
        @NonNull
        String sha256(@NonNull File file) throws IOException;
    }

    private static final class Entry {
        final long size;
        final long modifiedAt;
        final String sha256;

        Entry(long size, long modifiedAt, String sha256) {
            this.size = size;
            this.modifiedAt = modifiedAt;
            this.sha256 = sha256;
        }
    }

    @Nullable private final File storage;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean loaded;
    private boolean dirty;

    /**
     * @param storage where the cache persists, or {@code null} to keep it in memory only.
     */
    AttachmentHashCache(@Nullable File storage) {
        this.storage = storage;
    }

    /** The file's hash, from the cache when its size and modification time still match. */
    @NonNull
    synchronized String sha256(@NonNull File file, @NonNull Hasher hasher) throws IOException {
        load();
        String key = file.getAbsolutePath();
        long size = file.length();
        long modifiedAt = file.lastModified();
        Entry cached = entries.get(key);
        if (cached != null && cached.size == size && cached.modifiedAt == modifiedAt) {
            return cached.sha256;
        }
        String hash = hasher.sha256(file);
        entries.put(key, new Entry(size, modifiedAt, hash));
        dirty = true;
        return hash;
    }

    /** Writes the cache if anything changed; a failure here is logged by nobody on purpose. */
    synchronized void flush() {
        if (!dirty || storage == null) {
            return;
        }
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            JsonObject value = new JsonObject();
            value.addProperty("size", entry.getValue().size);
            value.addProperty("modifiedAt", entry.getValue().modifiedAt);
            value.addProperty("sha256", entry.getValue().sha256);
            root.add(entry.getKey(), value);
        }
        File parent = storage.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return;
        }
        File temporary = new File(parent, storage.getName() + ".tmp");
        try {
            Files.write(temporary.toPath(), root.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(
                    temporary.toPath(),
                    storage.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
        } catch (IOException | RuntimeException ignored) {
            // The next build simply hashes again.
            temporary.delete();
        }
    }

    /** Forgets everything, in memory and on disk. */
    synchronized void clear() {
        entries.clear();
        loaded = true;
        dirty = false;
        if (storage != null) {
            storage.delete();
        }
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (storage == null || !storage.isFile()) {
            return;
        }
        try {
            String json = new String(Files.readAllBytes(storage.toPath()), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonObject value = entry.getValue().getAsJsonObject();
                String sha256 = value.get("sha256").getAsString();
                if (!sha256.matches("[0-9a-f]{64}")) {
                    continue;
                }
                entries.put(
                        entry.getKey(),
                        new Entry(
                                value.get("size").getAsLong(),
                                value.get("modifiedAt").getAsLong(),
                                sha256));
            }
        } catch (IOException | RuntimeException unreadable) {
            entries.clear();
        }
    }
}
