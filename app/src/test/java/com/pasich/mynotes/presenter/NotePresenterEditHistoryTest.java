package com.pasich.mynotes.presenter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.base.BasePresenterTest;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.ui.contract.NoteContract;
import com.pasich.mynotes.ui.presenter.NotePresenter;
import com.pasich.mynotes.utils.constants.AutoSave;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import io.reactivex.Completable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.schedulers.TestScheduler;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;

/**
 * The editor's undo history belongs to the note on screen: it starts again whenever a note is put
 * into the editor, and saving never touches it.
 */
public class NotePresenterEditHistoryTest extends BasePresenterTest {

    @Mock DataManager dataManager;
    @Mock NoteContract.view view;

    private final TestScheduler debounceClock = new TestScheduler();
    private NotePresenter presenter;

    @Before
    public void setUp() {
        initMocks(this);
        when(dataManager.updateNote(any())).thenReturn(Completable.complete());
        presenter = new NotePresenter(provider(), new CompositeDisposable(), dataManager);
        presenter.attachView(view);
        presenter.setNewNoteKey(false);
        presenter.setIdKey(1L);
        when(dataManager.getNoteForId(1L)).thenReturn(Single.just(note(1, "stored text")));
    }

    private static Note note(int id, String value) {
        Note note = new Note().create("Title", value, new Date().getTime(), "");
        note.setId(id);
        note.setValueJson("");
        note.setAttachments("[]");
        return note;
    }

    private SchedulerProvider provider() {
        return new SchedulerProvider() {
            @Override
            public Scheduler ui() {
                return Schedulers.trampoline();
            }

            @Override
            public Scheduler computation() {
                return debounceClock;
            }

            @Override
            public Scheduler io() {
                return Schedulers.trampoline();
            }
        };
    }

    @Test
    public void loadingANoteStartsTheHistoryAgainAfterTheNoteIsShown() {
        presenter.loadingData(1L);

        InOrder order = inOrder(view);
        order.verify(view).loadingNote(any());
        order.verify(view).resetEditHistory();
    }

    @Test
    public void autosaveDoesNotTouchTheHistory() {
        presenter.loadingData(1L);

        presenter.simpleNoteChange(null, "typed", false);
        debounceClock.advanceTimeBy(AutoSave.AUTO_SAVE_DELAY, TimeUnit.MILLISECONDS);
        presenter.simpleNoteChange(null, "typed more", false);
        presenter.flushPending();
        debounceClock.advanceTimeBy(5, TimeUnit.SECONDS);

        verify(dataManager, times(2)).updateNote(any());
        verify(view, times(1)).resetEditHistory();
    }

    @Test
    public void extendedAutosaveDoesNotTouchTheHistory() {
        presenter.setExtendedEditor(true);
        presenter.loadingData(1L);

        presenter.extendedNoteChange(
                null, "[{\"id\":\"a\",\"type\":\"paragraph\",\"data\":{\"text\":\"x\"}}]");
        debounceClock.advanceTimeBy(AutoSave.AUTO_SAVE_DELAY, TimeUnit.MILLISECONDS);

        verify(dataManager, times(1)).updateNote(any());
        verify(view, times(1)).resetEditHistory();
    }

    @Test
    public void restoringAVersionStartsTheHistoryAgain() {
        presenter.loadingData(1L);

        presenter.reloadNote();

        verify(view, times(2)).resetEditHistory();
    }

    @Test
    public void aCopyStartsItsOwnHistory() {
        presenter.loadingData(1L);
        when(dataManager.copyNote(any())).thenReturn(Single.just(2L));
        when(dataManager.getNoteForId(2L)).thenReturn(Single.just(note(2, "stored text")));

        presenter.copyNoteRequest();

        verify(view, times(2)).resetEditHistory();
    }

    @Test
    public void aNoteThatFailsToLoadLeavesTheHistoryAlone() {
        when(dataManager.getNoteForId(1L)).thenReturn(Single.error(new RuntimeException("db")));

        presenter.loadingData(1L);

        verify(view, never()).resetEditHistory();
    }
}
