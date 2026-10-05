package com.pasich.mynotes.presenter;

import static com.google.common.truth.Truth.assertThat;
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
import com.pasich.mynotes.utils.constants.AutoSave;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import io.reactivex.Completable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.schedulers.TestScheduler;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * Leaving the editor must never lose what was typed in the autosave's two-second window, and the
 * extended editor's document is parsed when it is saved rather than on every change.
 */
public class NotePresenterFlushTest extends BasePresenterTest {

    private static final String DOC_HELLO =
            "[{\"id\":\"a1\",\"type\":\"paragraph\",\"data\":{\"text\":\"hello\"}}]";
    private static final String DOC_EMPTY =
            "[{\"id\":\"a1\",\"type\":\"paragraph\",\"data\":{\"text\":\"\"}}]";

    @Mock DataManager dataManager;
    @Mock NoteContract.view view;

    private final TestScheduler debounceClock = new TestScheduler();
    private final TestScheduler ioScheduler = new TestScheduler();
    private final List<String> writtenValues = new ArrayList<>();
    private NotePresenter presenter;
    private Note note;

    @Before
    public void setUp() {
        initMocks(this);
        when(dataManager.updateNote(any()))
                .thenAnswer(
                        invocation -> {
                            Note written = invocation.getArgument(0);
                            return Completable.fromAction(
                                    () -> writtenValues.add(written.getValue()));
                        });
        presenter = new NotePresenter(provider(), new CompositeDisposable(), dataManager);
        presenter.attachView(view);

        note = new Note().create("Title", "stored text", new Date().getTime(), "");
        note.setValueJson("");
        note.setAttachments("[]");
        when(dataManager.getNoteForId(1L)).thenReturn(Single.just(note));
        presenter.setNewNoteKey(false);
        presenter.setIdKey(1L);
        // Open the note the way the screen does, so the presenter knows what is stored.
        presenter.loadingData(1L);
        ioScheduler.triggerActions();
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
                return ioScheduler;
            }
        };
    }

    @Test
    public void editInsideTheDebounceIsNotWrittenUntilThePause() {
        presenter.simpleNoteChange(null, "typed just now", false);
        ioScheduler.triggerActions();

        assertThat(writtenValues).isEmpty();
        assertThat(presenter.hasUnsavedChanges()).isTrue();
    }

    @Test
    public void flushWritesTheEditStillInsideTheDebounce() {
        presenter.simpleNoteChange(null, "typed just now", false);

        presenter.flushPending();
        ioScheduler.triggerActions();

        assertThat(writtenValues).containsExactly("typed just now");
        assertThat(presenter.hasUnsavedChanges()).isFalse();
    }

    @Test
    public void debounceAfterAFlushDoesNotWriteTheSameTextAgain() {
        presenter.simpleNoteChange(null, "typed just now", false);
        presenter.flushPending();
        ioScheduler.triggerActions();

        debounceClock.advanceTimeBy(AutoSave.AUTO_SAVE_DELAY, TimeUnit.MILLISECONDS);
        ioScheduler.triggerActions();

        assertThat(writtenValues).containsExactly("typed just now");
    }

    @Test
    public void flushWithNothingChangedWritesNothing() {
        presenter.flushPending();
        ioScheduler.triggerActions();

        verify(dataManager, never()).updateNote(any());
    }

    @Test
    public void flushOfAnEmptiedNoteWritesNothing() {
        presenter.simpleNoteChange("", "   ", false);

        presenter.flushPending();
        ioScheduler.triggerActions();

        verify(dataManager, never()).updateNote(any());
    }

    @Test
    public void flushIsNotCancelledWhenTheScreenIsDestroyedRightAfterIt() {
        // Rotation: onStop flushes, onDestroy detaches before the write has run.
        presenter.simpleNoteChange(null, "typed before rotating", false);
        presenter.flushPending();
        presenter.detachView();

        ioScheduler.triggerActions();

        assertThat(writtenValues).containsExactly("typed before rotating");
    }

    @Test
    public void debounceStillSavesOnceTypingPauses() {
        presenter.simpleNoteChange(null, "a", false);
        presenter.simpleNoteChange(null, "ab", false);
        presenter.simpleNoteChange(null, "abc", false);

        debounceClock.advanceTimeBy(AutoSave.AUTO_SAVE_DELAY, TimeUnit.MILLISECONDS);
        ioScheduler.triggerActions();

        assertThat(writtenValues).containsExactly("abc");
    }

    @Test
    public void extendedDocumentIsParsedWhenItIsSaved() {
        presenter.setExtendedEditor(true);

        presenter.extendedNoteChange(null, DOC_HELLO);
        presenter.flushPending();
        ioScheduler.triggerActions();

        ArgumentCaptor<Note> written = ArgumentCaptor.forClass(Note.class);
        verify(dataManager, times(1)).updateNote(written.capture());
        assertThat(written.getValue().getValueJson()).isEqualTo(DOC_HELLO);
    }

    @Test
    public void extendedChangeWaitsForThePauseLikeTyping() {
        presenter.setExtendedEditor(true);

        presenter.extendedNoteChange(null, DOC_HELLO);
        ioScheduler.triggerActions();

        verify(dataManager, never()).updateNote(any());
        assertThat(presenter.hasUnsavedChanges()).isTrue();
    }

    @Test
    public void emptiedExtendedNoteNeitherSavesNorCleansAttachments() {
        // Cleaning against a list that was never written would delete files the stored note
        // still references.
        presenter.setExtendedEditor(true);

        presenter.extendedNoteChange("", DOC_EMPTY);
        debounceClock.advanceTimeBy(AutoSave.AUTO_SAVE_DELAY, TimeUnit.MILLISECONDS);
        ioScheduler.triggerActions();

        verify(dataManager, never()).updateNote(any());
        verify(view, never()).runAttachmentsCleanup(any());
    }
}
