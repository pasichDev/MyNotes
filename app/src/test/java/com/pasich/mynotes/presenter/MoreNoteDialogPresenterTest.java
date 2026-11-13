package com.pasich.mynotes.presenter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.base.BasePresenterTest;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.ui.contract.dialogs.MoreNoteDialogContract;
import com.pasich.mynotes.ui.presenter.dialogs.MoreNoteDialogPresenter;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import io.reactivex.Completable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.TestScheduler;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class MoreNoteDialogPresenterTest extends BasePresenterTest {

    @Mock DataManager dataManager;
    @Mock MoreNoteDialogContract.view view;

    private MoreNoteDialogPresenter presenter;

    @Before
    public void setUp() {
        initMocks(this);
        presenter =
                new MoreNoteDialogPresenter(
                        testSchedulerProvider(), new CompositeDisposable(), dataManager);
        presenter.attachView(view);
    }

    @Test
    public void viewIsReady_textSizeBelowSliderRange_usesMinimumSliderValue() {
        when(dataManager.getSizeTextNoteActivity()).thenReturn(0);

        presenter.viewIsReady();

        verify(view).setSliderValue(10);
    }

    @Test
    public void viewIsReady_textSizeAboveSliderRange_usesMaximumSliderValue() {
        when(dataManager.getSizeTextNoteActivity()).thenReturn(40);

        presenter.viewIsReady();

        verify(view).setSliderValue(32);
    }

    private void loadNote(MoreNoteDialogPresenter target) {
        Note note = new Note().create("Title", "Text", 1L, "");
        note.setId(5);
        when(dataManager.getNoteForId(5)).thenReturn(Single.just(note));
        target.loadNote(5);
    }

    /** A presenter whose io work waits for {@code io} to be triggered. */
    private MoreNoteDialogPresenter presenterWithQueuedIo(TestScheduler io) {
        SchedulerProvider immediate = testSchedulerProvider();
        SchedulerProvider queued =
                new SchedulerProvider() {
                    @Override
                    public Scheduler ui() {
                        return immediate.ui();
                    }

                    @Override
                    public Scheduler computation() {
                        return immediate.computation();
                    }

                    @Override
                    public Scheduler io() {
                        return io;
                    }
                };
        MoreNoteDialogPresenter target =
                new MoreNoteDialogPresenter(queued, new CompositeDisposable(), dataManager);
        target.attachView(view);
        return target;
    }

    @Test
    public void togglePin_dialogClosedBeforeTheWriteStarts_stillPins() {
        TestScheduler io = new TestScheduler();
        MoreNoteDialogPresenter target = presenterWithQueuedIo(io);
        loadNote(target);
        io.triggerActions();
        AtomicBoolean ran = new AtomicBoolean();
        when(dataManager.setPinNote(5, true))
                .thenReturn(Completable.fromAction(() -> ran.set(true)));

        target.togglePinNote();
        target.detachView(); // the sheet dismisses in the same click
        io.triggerActions();

        assertTrue(ran.get());
    }

    @Test
    public void copy_dialogClosedBeforeTheCopyIsWritten_stillOpensTheCopy() {
        TestScheduler io = new TestScheduler();
        MoreNoteDialogPresenter target = presenterWithQueuedIo(io);
        loadNote(target);
        io.triggerActions();
        when(dataManager.addNote(any())).thenReturn(Single.just(77L));
        AtomicLong opened = new AtomicLong();

        target.copyNoteMainActivity(opened::set);
        target.detachView();
        io.triggerActions();

        assertEquals(77L, opened.get());
    }
}
