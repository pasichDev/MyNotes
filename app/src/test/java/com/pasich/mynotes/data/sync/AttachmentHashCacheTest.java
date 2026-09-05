package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Every snapshot build used to re-hash every attachment in the library; the cache is what makes an
 * idle sync cheap. Correctness must never depend on it, so the tests also pin when it forgets.
 */
public class AttachmentHashCacheTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final AtomicInteger hashed = new AtomicInteger();
    private final AttachmentHashCache.Hasher counting =
            file -> {
                hashed.incrementAndGet();
                return Sha256.of(file);
            };

    @Test
    public void hashesAFileOnceWhileItIsUnchanged() throws Exception {
        File file = write("photo.png", "bytes");
        AttachmentHashCache cache = new AttachmentHashCache(storage());

        String first = cache.sha256(file, counting);
        String second = cache.sha256(file, counting);

        assertThat(second).isEqualTo(first);
        assertThat(first).isEqualTo(Sha256.of(file));
        assertThat(hashed.get()).isEqualTo(1);
    }

    @Test
    public void rehashesAFileWhoseContentChanged() throws Exception {
        File file = write("photo.png", "bytes");
        AttachmentHashCache cache = new AttachmentHashCache(storage());
        cache.sha256(file, counting);

        Files.write(file.toPath(), "different length".getBytes(StandardCharsets.UTF_8));
        String rehashed = cache.sha256(file, counting);

        assertThat(rehashed).isEqualTo(Sha256.of(file));
        assertThat(hashed.get()).isEqualTo(2);
    }

    @Test
    public void survivesTheStoreInstanceThatBuiltIt() throws Exception {
        // A store lives for one sync; without persistence the first build of every sync — and
        // the estimate before the first one — hashed the whole library again.
        File file = write("photo.png", "bytes");
        AttachmentHashCache first = new AttachmentHashCache(storage());
        first.sha256(file, counting);
        first.flush();

        AttachmentHashCache second = new AttachmentHashCache(storage());
        String hash = second.sha256(file, counting);

        assertThat(hash).isEqualTo(Sha256.of(file));
        assertThat(hashed.get()).isEqualTo(1);
    }

    @Test
    public void anUnreadableCacheFileCostsARehashNotAFailure() throws Exception {
        File file = write("photo.png", "bytes");
        assertThat(storage().getParentFile().mkdirs()).isTrue();
        Files.write(storage().toPath(), "{not json".getBytes(StandardCharsets.UTF_8));
        AttachmentHashCache cache = new AttachmentHashCache(storage());

        assertThat(cache.sha256(file, counting)).isEqualTo(Sha256.of(file));
        assertThat(hashed.get()).isEqualTo(1);
    }

    @Test
    public void clearForgetsEverything() throws Exception {
        File file = write("photo.png", "bytes");
        AttachmentHashCache cache = new AttachmentHashCache(storage());
        cache.sha256(file, counting);
        cache.flush();

        cache.clear();

        assertThat(storage().exists()).isFalse();
        cache.sha256(file, counting);
        assertThat(hashed.get()).isEqualTo(2);
    }

    private File storage() {
        return new File(temporaryFolder.getRoot(), "sync-attachments/hash-cache.json");
    }

    private File write(String name, String content) throws Exception {
        File file = new File(temporaryFolder.getRoot(), name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
