package com.pasich.mynotes.presenter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.base.BasePresenterTest;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.ui.contract.NoteContract;
import com.pasich.mynotes.ui.presenter.NotePresenter;
import io.reactivex.Completable;
import io.reactivex.disposables.CompositeDisposable;
import java.util.Date;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/**
 * A new note left untouched in the extended editor must be discarded, exactly as one left untouched
 * in the simple editor is.
 *
 * <p>It used to survive: the editor writes an empty paragraph into an opened note, and emptiness
 * was decided by comparing that serialized document against the string {@code []}. The note was
 * then saved on close and counted in the statistics.
 */
public class NotePresenterEmptyExtendedNoteTest extends BasePresenterTest {

    private static final String UNTOUCHED_DOCUMENT =
            "[{\"id\":\"a1\",\"type\":\"paragraph\",\"data\":{\"text\":\"\"}}]";

    @Mock DataManager mockDataManager;
    @Mock NoteContract.view mockView;
    NotePresenter presenter;

    @Before
    public void setUp() {
        initMocks(this);
        when(mockDataManager.updateNote(any())).thenReturn(Completable.complete());
        when(mockDataManager.deleteNote(any(Note.class))).thenReturn(Completable.complete());
        presenter =
                new NotePresenter(
                        testSchedulerProvider(), new CompositeDisposable(), mockDataManager);
        presenter.attachView(mockView);
        presenter.setExtendedEditor(true);
        presenter.setNewNoteKey(true);
        presenter.setIdKey(1L);
    }

    /** The note the editor hands back when it was opened and nothing was typed. */
    private Note untouchedNote() {
        Note note = new Note().create("", "", new Date().getTime(), "");
        note.setValueJson(UNTOUCHED_DOCUMENT);
        // extendedNoteChange() always writes the parsed attachment list; with no files that is
        // the empty list, which used to read as "this note has attachments".
        note.setAttachments("[]");
        return note;
    }

    @Test
    public void untouchedNewNoteIsDeletedOnClose() {
        Note note = untouchedNote();
        presenter.setNote(note);

        presenter.closeActivity();

        verify(mockDataManager, times(1)).deleteNote(note);
        verify(mockDataManager, never()).updateNote(any(Note.class));
        verify(mockView).closeNoteActivity();
    }

    @Test
    public void untouchedNewNoteIsNotAutoSaved() {
        presenter.setNote(untouchedNote());

        presenter.onNoteChanged();

        verify(mockDataManager, never()).updateNote(any(Note.class));
    }

    @Test
    public void newNoteWithTypedTextIsSavedOnClose() {
        Note note = new Note().create("", "", new Date().getTime(), "");
        note.setValueJson("[{\"id\":\"a1\",\"type\":\"paragraph\",\"data\":{\"text\":\"hi\"}}]");
        note.setValue("hi");
        note.setAttachments("[]");
        presenter.setNote(note);

        presenter.closeActivity();

        verify(mockDataManager, times(1)).updateNote(note);
        verify(mockDataManager, never()).deleteNote(any(Note.class));
    }

    /**
     * Deliberate, and the same answer the simple editor has always given: emptying a note is not a
     * way to erase it, so the stored text stays and the note is not deleted either. Pinned because
     * the two editors disagreed here until the emptiness rule was shared.
     */
    @Test
    public void existingNoteEmptiedByTheUserKeepsWhatWasStored() {
        presenter.setNewNoteKey(false);
        Note note = new Note().create("", "old text", new Date().getTime(), "");
        note.setValueJson("[{\"type\":\"paragraph\",\"data\":{\"text\":\"old text\"}}]");
        presenter.setNote(note);

        // The editor keeps emitting a document after the user clears it: one empty paragraph.
        presenter.extendedNoteChange(
                "", "[{\"id\":\"a1\",\"type\":\"paragraph\",\"data\":{\"text\":\"\"}}]");
        presenter.closeActivity();

        verify(mockDataManager, never()).updateNote(any(Note.class));
        verify(mockDataManager, never()).deleteNote(any(Note.class));
        verify(mockView).closeNoteActivity();
    }

    @Test
    public void newNoteWithOnlyAnAttachmentIsSavedOnClose() {
        Note note = new Note().create("", "", new Date().getTime(), "");
        note.setValueJson(
                "[{\"type\":\"attaches\",\"data\":{\"file\":{\"url\":\"editorjs://attachments/note_1/one.pdf\"}}}]");
        note.setAttachments("[{\"url\":\"editorjs://attachments/note_1/one.pdf\"}]");
        presenter.setNote(note);

        presenter.closeActivity();

        verify(mockDataManager, times(1)).updateNote(note);
        verify(mockDataManager, never()).deleteNote(any(Note.class));
    }
}
