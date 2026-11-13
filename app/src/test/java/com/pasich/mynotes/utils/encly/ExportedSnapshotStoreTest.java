package com.pasich.mynotes.utils.encly;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * What a hand-off carried is kept on disk instead of in the saved instance state, which a large
 * library overflowed; it has to come back exactly, and only under its own token.
 */
public class ExportedSnapshotStoreTest {

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private static EnclyMigrationRepository.Exported snapshot(int records) {
        long[] ids = new long[records];
        long[] prints = new long[records];
        for (int i = 0; i < records; i++) {
            ids[i] = i + 1;
            prints[i] = 31L * i - 7L;
        }
        return new EnclyMigrationRepository.Exported(
                ids,
                new long[] {5},
                new long[0],
                new long[] {2, 3},
                prints,
                new long[] {-1},
                new long[0],
                new long[] {Long.MAX_VALUE, Long.MIN_VALUE});
    }

    @Test
    public void aSavedSnapshot_readsBackExactly() throws IOException {
        File dir = new File(folder.getRoot(), "encly");
        EnclyMigrationRepository.Exported saved = snapshot(50_000);

        String token = ExportedSnapshotStore.save(dir, saved);
        EnclyMigrationRepository.Exported loaded = ExportedSnapshotStore.load(dir, token);

        assertNotNull(loaded);
        assertArrayEquals(saved.notes, loaded.notes);
        assertArrayEquals(saved.tasks, loaded.tasks);
        assertArrayEquals(saved.tags, loaded.tags);
        assertArrayEquals(saved.categories, loaded.categories);
        assertArrayEquals(saved.noteFingerprints, loaded.noteFingerprints);
        assertArrayEquals(saved.taskFingerprints, loaded.taskFingerprints);
        assertArrayEquals(saved.tagFingerprints, loaded.tagFingerprints);
        assertArrayEquals(saved.categoryFingerprints, loaded.categoryFingerprints);
    }

    @Test
    public void aNewHandoff_replacesTheOldSnapshot() throws IOException {
        File dir = folder.getRoot();
        String first = ExportedSnapshotStore.save(dir, snapshot(3));

        String second = ExportedSnapshotStore.save(dir, snapshot(4));

        assertNull(ExportedSnapshotStore.load(dir, first));
        assertEquals(4, ExportedSnapshotStore.load(dir, second).notes.length);
    }

    @Test
    public void anUnknownOrMalformedToken_readsNothing() throws IOException {
        File dir = folder.getRoot();
        ExportedSnapshotStore.save(dir, snapshot(3));

        assertNull(ExportedSnapshotStore.load(dir, null));
        assertNull(ExportedSnapshotStore.load(dir, "1-abc"));
        assertNull(ExportedSnapshotStore.load(dir, "../../etc/passwd"));
    }

    @Test
    public void aDamagedFile_readsNothing() throws IOException {
        File dir = folder.getRoot();
        String token = ExportedSnapshotStore.save(dir, snapshot(3));
        File file = new File(dir, "exported-" + token + ".bin");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[] {0, 0, 0, 1, 0x7f, 0x7f, 0x7f, 0x7f});
        }

        assertNull(ExportedSnapshotStore.load(dir, token));
    }

    @Test
    public void aSweep_keepsSnapshotsSavedAfterItWasAskedFor() throws IOException {
        File dir = folder.getRoot();
        String token = ExportedSnapshotStore.save(dir, snapshot(3));
        long savedAt = Long.parseLong(token.substring(0, token.indexOf('-')));

        assertEquals(0, ExportedSnapshotStore.sweep(dir, savedAt));
        assertNotNull(ExportedSnapshotStore.load(dir, token));

        assertEquals(1, ExportedSnapshotStore.sweep(dir, Long.MAX_VALUE));
        assertNull(ExportedSnapshotStore.load(dir, token));
    }
}
