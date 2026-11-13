package com.pasich.mynotes.presenter;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.base.BasePresenterTest;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.database.NoteDeletedException;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.ui.contract.NoteContract;
import com.pasich.mynotes.ui.presenter.NotePresenter;
import com.pasich.mynotes.utils.constants.AutoSave;
import com.pasich.mynotes.utils.enums.SaveState;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import io.reactivex.Completable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.schedulers.TestScheduler;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * A note deleted while it is open (a sync applying another device's delete) must not swallow what
 * is typed into it: the save fails in the database, and the presenter puts the note back under its
 * own id. The id has to stay: the screen's intent, saved state and draft, and the note's attachment
 * folder all go by it.
 */
public class NotePresenterDeletedNoteTest extends BasePresenterTest {

    private static final int DELETED_ID = 1;

    @Mock DataManager dataManager;
    @Mock NoteContract.view view;

    private final TestScheduler debounceClock = new TestScheduler();
    private final Set<Integer> existingIds = new HashSet<>();
    private final List<String> written = new ArrayList<>();
    private NotePresenter presenter;

    @Before
    public void setUp() {
        initMocks(this);
        existingIds.add(DELETED_ID);
        when(dataManager.updateNote(any()))
                .thenAnswer(invocation -> {
                    Note note = invocation.getArgument(0);
                    return Completable.fromAction(() -> {
                        if (!existingIds.contains(note.getId())) {
                            throw new NoteDeletedException(note.getId());
                        }
                        written.add(note.getId() + ":" + note.getValue());
                    });
                });
        when(dataManager.reviveNote(any()))
                .thenAnswer(invocation -> {
                    Note note = invocation.getArgument(0);
                    return Completable.fromAction(() -> {
                        existingIds.add(note.getId());
                        written.add("revived " + note.getId() + ":" + note.getValue());
                    });
                });
        presenter = new NotePresenter(provider(), new CompositeDisposable(), dataManager);
        presenter.attachView(view);

        Note stored = new Note().create("Title", "stored text", new Date().getTime(), "");
        stored.setId(DELETED_ID);
        stored.setValueJson("");
        stored.setAttachments("[]");
        when(dataManager.getNoteForId(DELETED_ID)).thenReturn(Single.just(stored));
        presenter.setNewNoteKey(false);
        presenter.setIdKey(DELETED_ID);
        presenter.loadingData(DELETED_ID);
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

    private void typeAndWait(String value) {
        presenter.simpleNoteChange(null, value, false);
        debounceClock.advanceTimeBy(AutoSave.AUTO_SAVE_DELAY, TimeUnit.MILLISECONDS);
    }

    @Test
    public void anEditToADeletedNote_revivesItUnderItsOwnId() {
        existingIds.remove(DELETED_ID);

        typeAndWait("typed after the delete");

        assertThat(written).containsExactly("revived " + DELETED_ID + ":typed after the delete");
        assertThat(presenter.getIdKey()).isEqualTo(DELETED_ID);
        assertThat(presenter.getNote().getId()).isEqualTo(DELETED_ID);
        assertThat(presenter.hasUnsavedChanges()).isFalse();
        verify(dataManager, never()).addNote(any());
    }

    @Test
    public void laterEdits_updateTheRevivedNote() {
        existingIds.remove(DELETED_ID);
        typeAndWait("first");

        typeAndWait("second");

        assertThat(written)
                .containsExactly("revived " + DELETED_ID + ":first", DELETED_ID + ":second")
                .inOrder();
        verify(dataManager, times(1)).reviveNote(any());
    }

    @Test
    public void flushOfADeletedNote_revivesIt() {
        existingIds.remove(DELETED_ID);
        presenter.simpleNoteChange(null, "typed before leaving", false);

        presenter.flushPending();

        assertThat(written).containsExactly("revived " + DELETED_ID + ":typed before leaving");
    }

    @Test
    public void otherErrors_areReportedAndCreateNothing() {
        when(dataManager.updateNote(any()))
                .thenReturn(Completable.error(new IllegalStateException("disk full")));

        typeAndWait("typed");

        verify(dataManager, never()).reviveNote(any());
        verify(dataManager, never()).addNote(any());
        ArgumentCaptor<SaveState> states = ArgumentCaptor.forClass(SaveState.class);
        verify(view, org.mockito.Mockito.atLeastOnce()).updateSaveStatus(states.capture());
        assertThat(states.getAllValues()).contains(SaveState.ERROR);
        assertThat(presenter.hasUnsavedChanges()).isTrue();
    }

    @Test
    public void anExistingNote_isUpdatedInPlace() {
        typeAndWait("typed");

        assertThat(written).containsExactly(DELETED_ID + ":typed");
        verify(dataManager, never()).reviveNote(any());
        assertThat(presenter.getIdKey()).isEqualTo(DELETED_ID);
    }
}
