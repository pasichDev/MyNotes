package com.pasich.mynotes.utils.encly;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * The clear after a hand-off deletes what the hand-off carried, and nothing added or changed since.
 */
public class EnclyExportedSnapshotTest {

    private static Note note(int id) {
        return note(id, "text " + id);
    }

    private static Note note(int id, String value) {
        Note note = new Note().create("title", value, 0L, "");
        note.setId(id);
        note.setValueJson("");
        note.setAttachments("[]");
        return note;
    }

    private static EnclyMigrationRepository.Library library(List<Note> notes) {
        return new EnclyMigrationRepository.Library(
                notes, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    private static List<Note> toDelete(List<Note> now, EnclyMigrationRepository.Exported exported) {
        return EnclyMigrationRepository.onlyExportedUnchanged(
                now,
                exported.notes,
                exported.noteFingerprints,
                Note::getId,
                EnclyMigrationRepository::fingerprint);
    }

    @Test
    public void keepsOnlyTheRecordsTheHandoffCarried() {
        EnclyMigrationRepository.Exported exported =
                EnclyMigrationRepository.Exported.of(
                        library(Arrays.asList(note(1), note(3), note(4))));
        List<Note> now = Arrays.asList(note(1), note(2), note(3), note(9));

        List<Note> deleted = toDelete(now, exported);

        assertEquals(2, deleted.size());
        assertEquals(1, deleted.get(0).getId());
        assertEquals(3, deleted.get(1).getId());
    }

    @Test
    public void aNoteChangedSinceTheHandoff_isKept() {
        EnclyMigrationRepository.Exported exported =
                EnclyMigrationRepository.Exported.of(
                        library(Arrays.asList(note(1, "carried"), note(2, "carried"))));
        // Note 2 was edited, here or by a sync, after the archive was written.
        List<Note> now = Arrays.asList(note(1, "carried"), note(2, "edited later"));

        List<Note> deleted = toDelete(now, exported);

        assertEquals(1, deleted.size());
        assertEquals(1, deleted.get(0).getId());
    }

    @Test
    public void aNoteRetitledOrRetaggedSinceTheHandoff_isKept() {
        EnclyMigrationRepository.Exported exported =
                EnclyMigrationRepository.Exported.of(library(Collections.singletonList(note(1))));
        Note retitled = note(1);
        retitled.setTitle("new title");
        Note retagged = note(1);
        retagged.setTag("work");

        assertTrue(toDelete(Collections.singletonList(retitled), exported).isEmpty());
        assertTrue(toDelete(Collections.singletonList(retagged), exported).isEmpty());
    }

    @Test
    public void onlyANewEditDate_doesNotKeepTheNote() {
        EnclyMigrationRepository.Exported exported =
                EnclyMigrationRepository.Exported.of(library(Collections.singletonList(note(1))));
        Note touched = note(1);
        touched.setDate(12345L);
        touched.setCustomPosition(99L);

        assertEquals(1, toDelete(Collections.singletonList(touched), exported).size());
    }

    @Test
    public void changedTasksTagsAndCategories_changeTheirFingerprints() {
        Task task = new Task();
        task.setTitle("buy milk");
        long taskPrint = EnclyMigrationRepository.fingerprint(task);
        task.setPosition(5);
        assertEquals(taskPrint, EnclyMigrationRepository.fingerprint(task));
        task.setDone(true);
        assertNotEquals(taskPrint, EnclyMigrationRepository.fingerprint(task));

        Tag tag = new Tag();
        tag.setNameTag("home");
        long tagPrint = EnclyMigrationRepository.fingerprint(tag);
        tag.setNameTag("house");
        assertNotEquals(tagPrint, EnclyMigrationRepository.fingerprint(tag));

        TaskCategory category = new TaskCategory("errands", "#000000");
        long categoryPrint = EnclyMigrationRepository.fingerprint(category);
        category.setName("chores");
        assertNotEquals(categoryPrint, EnclyMigrationRepository.fingerprint(category));
    }

    @Test
    public void partsCannotBlurIntoEachOther() {
        Note a = note(1, "bc");
        a.setTitle("a");
        Note b = note(1, "c");
        b.setTitle("ab");

        assertNotEquals(
                EnclyMigrationRepository.fingerprint(a), EnclyMigrationRepository.fingerprint(b));
    }

    @Test
    public void nothingExported_deletesNothing() {
        EnclyMigrationRepository.Exported none =
                EnclyMigrationRepository.Exported.of(library(new ArrayList<>()));

        assertTrue(toDelete(Arrays.asList(note(1), note(2)), none).isEmpty());
        assertTrue(
                EnclyMigrationRepository.onlyExportedUnchanged(
                                null,
                                new long[] {1},
                                new long[] {0},
                                Note::getId,
                                EnclyMigrationRepository::fingerprint)
                        .isEmpty());
    }

    @Test
    public void fingerprintsThatDoNotMatchTheIds_deleteNothing() {
        assertTrue(
                EnclyMigrationRepository.onlyExportedUnchanged(
                                Collections.singletonList(note(1)),
                                new long[] {1},
                                new long[0],
                                Note::getId,
                                EnclyMigrationRepository::fingerprint)
                        .isEmpty());
    }

    @Test
    public void snapshot_recordsEveryIdOfTheLibraryWithItsFingerprint() {
        EnclyMigrationRepository.Exported exported =
                EnclyMigrationRepository.Exported.of(library(Arrays.asList(note(5), note(6))));

        assertArrayEquals(new long[] {5, 6}, exported.notes);
        assertEquals(2, exported.noteFingerprints.length);
        assertEquals(EnclyMigrationRepository.fingerprint(note(5)), exported.noteFingerprints[0]);
        assertEquals(0, exported.tasks.length);
        assertEquals(0, exported.taskFingerprints.length);
        assertEquals(0, exported.tags.length);
        assertEquals(0, exported.categories.length);
    }
}
