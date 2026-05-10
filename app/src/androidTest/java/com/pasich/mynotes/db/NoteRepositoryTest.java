package com.pasich.mynotes.db;

import static com.google.common.truth.Truth.assertThat;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.dao.NoteDao;
import com.pasich.mynotes.data.model.Note;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class NoteRepositoryTest {

    private AppDatabase db;
    private NoteDao noteDao;

    @Before
    public void setUp() {
        db = Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                AppDatabase.class
        ).allowMainThreadQueries().build();
        noteDao = db.noteDao();
    }

    @After
    public void tearDown() {
        db.close();
    }

    private Note makeNote(String title, String value) {
        return new Note().create(title, value, new Date().getTime(), "");
    }

    @Test
    public void addNote_andQueryAll_returnsNote() {
        noteDao.addNote(makeNote("Hello", "World"));
        List<Note> result = noteDao.getNotesAll().blockingFirst();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Hello");
    }

    @Test
    public void addNote_isNotInTrash_byDefault() {
        noteDao.addNote(makeNote("Draft", "content"));
        List<Note> notes = noteDao.getNotesAll().blockingFirst();
        assertThat(notes.get(0).isTrash()).isFalse();
    }

    @Test
    public void deleteNote_removesFromDb() {
        Note note = makeNote("Delete me", "soon");
        long id = noteDao.addNote(note);
        note.setId((int) id);
        noteDao.deleteNote(note);
        List<Note> result = noteDao.getNotesAll().blockingFirst();
        assertThat(result).isEmpty();
    }

    @Test
    public void updateNote_changesTitle() {
        Note note = makeNote("Old", "value");
        long id = noteDao.addNote(note);
        note.setId((int) id);
        note.setTitle("New");
        noteDao.updateNote(note);
        Note updated = noteDao.getNoteForId(id).blockingGet();
        assertThat(updated.getTitle()).isEqualTo("New");
    }

    @Test
    public void moveToTrash_notesAppearInTrash() {
        Note note = makeNote("Trash me", "bye");
        int id = noteDao.addNote(note).intValue();
        noteDao.moveNotesToTrash(Collections.singletonList(id));
        List<Note> trash = noteDao.getTrashNotes().blockingFirst();
        assertThat(trash).hasSize(1);
        assertThat(trash.get(0).isTrash()).isTrue();
    }

    @Test
    public void moveToTrash_notesDisappearFromMain() {
        Note note = makeNote("Moving", "out");
        int id = noteDao.addNote(note).intValue();
        noteDao.moveNotesToTrash(Collections.singletonList(id));
        List<Note> main = noteDao.getNotesAll().blockingFirst();
        assertThat(main).isEmpty();
    }

    @Test
    public void restoreFromTrash_notesReturnToMain() {
        Note note = makeNote("Restore me", "please");
        int id = noteDao.addNote(note).intValue();
        noteDao.moveNotesToTrash(Collections.singletonList(id));
        noteDao.restoreNotesFromTrash(Collections.singletonList(id));
        List<Note> main = noteDao.getNotesAll().blockingFirst();
        assertThat(main).hasSize(1);
        assertThat(main.get(0).isTrash()).isFalse();
    }

    @Test
    public void deleteAllTrashNotes_clearsTrash() {
        Note note = makeNote("Trash", "gone");
        int id = noteDao.addNote(note).intValue();
        noteDao.moveNotesToTrash(Collections.singletonList(id));
        noteDao.deleteAllTrashNotes();
        List<Note> trash = noteDao.getTrashNotes().blockingFirst();
        assertThat(trash).isEmpty();
    }

    @Test
    public void getNoteForId_returnsCorrectNote() {
        long idA = noteDao.addNote(makeNote("Alpha", "a"));
        noteDao.addNote(makeNote("Beta", "b"));
        Note found = noteDao.getNoteForId(idA).blockingGet();
        assertThat(found.getTitle()).isEqualTo("Alpha");
    }

    @Test
    public void addMultipleNotes_allAppearInList() {
        noteDao.addNote(makeNote("One", "1"));
        noteDao.addNote(makeNote("Two", "2"));
        noteDao.addNote(makeNote("Three", "3"));
        List<Note> all = noteDao.getNotesAll().blockingFirst();
        assertThat(all).hasSize(3);
    }
}
