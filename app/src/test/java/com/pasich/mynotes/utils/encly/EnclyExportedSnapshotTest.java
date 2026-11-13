package com.pasich.mynotes.utils.encly;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.pasich.mynotes.data.model.Note;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The clear after a hand-off deletes what the hand-off carried, and nothing added since. */
public class EnclyExportedSnapshotTest {

    private static Note note(int id) {
        Note note = new Note();
        note.setId(id);
        return note;
    }

    @Test
    public void keepsOnlyTheRecordsTheHandoffCarried() {
        List<Note> now = Arrays.asList(note(1), note(2), note(3), note(9));

        List<Note> toDelete =
                EnclyMigrationRepository.onlyExported(now, new long[] {1, 3, 4}, Note::getId);

        assertEquals(2, toDelete.size());
        assertEquals(1, toDelete.get(0).getId());
        assertEquals(3, toDelete.get(1).getId());
    }

    @Test
    public void nothingExported_deletesNothing() {
        assertTrue(
                EnclyMigrationRepository.onlyExported(
                                Arrays.asList(note(1), note(2)), new long[0], Note::getId)
                        .isEmpty());
        assertTrue(
                EnclyMigrationRepository.onlyExported(null, new long[] {1}, Note::getId).isEmpty());
    }

    @Test
    public void snapshot_recordsEveryIdOfTheLibrary() {
        EnclyMigrationRepository.Library library =
                new EnclyMigrationRepository.Library(
                        Arrays.asList(note(5), note(6)),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new ArrayList<>());

        EnclyMigrationRepository.Exported exported = EnclyMigrationRepository.Exported.of(library);

        assertArrayEquals(new long[] {5, 6}, exported.notes);
        assertEquals(0, exported.tasks.length);
        assertEquals(0, exported.tags.length);
        assertEquals(0, exported.categories.length);
    }
}
