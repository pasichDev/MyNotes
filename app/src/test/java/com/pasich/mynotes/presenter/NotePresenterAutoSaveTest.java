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

public class NotePresenterAutoSaveTest extends BasePresenterTest {

    @Mock DataManager mockDataManager;
    @Mock NoteContract.view mockView;
    NotePresenter presenter;
    Note testNote;

    @Before
    public void setUp() {
        initMocks(this);
        when(mockDataManager.updateNote(any())).thenReturn(Completable.complete());
        presenter =
                new NotePresenter(
                        testSchedulerProvider(), new CompositeDisposable(), mockDataManager);
        presenter.attachView(mockView);

        testNote = new Note().create("Test Title", "Test content", new Date().getTime(), "");
        presenter.setNote(testNote);
        presenter.setNewNoteKey(false);
        presenter.setIdKey(1L);
    }

    @Test
    public void onNoteChanged_calledOnce_savesExactlyOnce() {
        presenter.onNoteChanged();
        verify(mockDataManager, times(1)).updateNote(any(Note.class));
    }

    @Test
    public void onNoteChanged_calledFiveTimes_savesOnlyOnce() {
        presenter.onNoteChanged();
        presenter.onNoteChanged();
        presenter.onNoteChanged();
        presenter.onNoteChanged();
        presenter.onNoteChanged();
        verify(mockDataManager, times(1)).updateNote(any(Note.class));
    }

    @Test
    public void onNoteChanged_afterDetach_doesNotCrash() {
        presenter.detachView();
        presenter.onNoteChanged();
        verify(mockDataManager, never()).updateNote(any());
    }

    @Test
    public void onNoteChanged_emptyNote_doesNotSave() {
        Note emptyNote = new Note().create("", "", new Date().getTime(), "");
        presenter.setNote(emptyNote);
        presenter.onNoteChanged();
        verify(mockDataManager, never()).updateNote(any());
    }
}
