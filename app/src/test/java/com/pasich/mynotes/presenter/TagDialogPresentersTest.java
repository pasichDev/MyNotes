package com.pasich.mynotes.presenter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.base.BasePresenterTest;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.contract.dialogs.DeleteTagDialogContract;
import com.pasich.mynotes.ui.contract.dialogs.NameTagDialogContract;
import com.pasich.mynotes.ui.presenter.dialogs.DeleteTagDialogPresenter;
import com.pasich.mynotes.ui.presenter.dialogs.NameTagDialogPresenter;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import io.reactivex.Completable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.TestScheduler;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class TagDialogPresentersTest extends BasePresenterTest {

    @Mock DataManager dataManager;
    @Mock DeleteTagDialogContract.view deleteView;
    @Mock NameTagDialogContract.view nameView;

    private final TestScheduler io = new TestScheduler();

    @Before
    public void setUp() {
        initMocks(this);
    }

    /** io work waits for {@link #io} to be triggered, like a busy io pool. */
    private SchedulerProvider queuedIo() {
        SchedulerProvider immediate = testSchedulerProvider();
        return new SchedulerProvider() {
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
    }

    private static Completable flagging(AtomicBoolean ran) {
        return Completable.fromAction(() -> ran.set(true));
    }

    @Test
    public void deleteTag_dialogClosedBeforeTheWriteStarts_stillDeletes() {
        AtomicBoolean ran = new AtomicBoolean();
        Tag tag = new Tag().create("Work");
        when(dataManager.clearTagInNotes(tag)).thenReturn(flagging(ran));
        DeleteTagDialogPresenter presenter =
                new DeleteTagDialogPresenter(queuedIo(), new CompositeDisposable(), dataManager);
        presenter.attachView(deleteView);

        presenter.deleteTagUnchecked(tag);
        presenter.detachView(); // the dialog dismisses in the same click
        io.triggerActions();

        org.junit.Assert.assertTrue(ran.get());
    }

    @Test
    public void deleteTagAndNotes_dialogClosedBeforeTheWriteStarts_stillDeletes() {
        AtomicBoolean ran = new AtomicBoolean();
        Tag tag = new Tag().create("Work");
        when(dataManager.deleteTagAndMoveNotesToTrash(tag)).thenReturn(flagging(ran));
        DeleteTagDialogPresenter presenter =
                new DeleteTagDialogPresenter(queuedIo(), new CompositeDisposable(), dataManager);
        presenter.attachView(deleteView);

        presenter.deleteTagAndNotes(tag);
        presenter.detachView();
        io.triggerActions();

        org.junit.Assert.assertTrue(ran.get());
    }

    @Test
    public void notesCount_arrivesLater_isShown() {
        when(dataManager.getCountNotesTag("Work")).thenReturn(Single.just(3));
        DeleteTagDialogPresenter presenter =
                new DeleteTagDialogPresenter(
                        testSchedulerProvider(), new CompositeDisposable(), dataManager);
        presenter.attachView(deleteView);

        presenter.getLoadCountNotesForTag("Work");

        verify(deleteView).showNotesCount(3);
    }

    @Test
    public void notesCount_failing_doesNotCrash() {
        when(dataManager.getCountNotesTag("Work"))
                .thenReturn(Single.error(new IllegalStateException("db closed")));
        DeleteTagDialogPresenter presenter =
                new DeleteTagDialogPresenter(
                        testSchedulerProvider(), new CompositeDisposable(), dataManager);
        presenter.attachView(deleteView);

        presenter.getLoadCountNotesForTag("Work");
    }

    @Test
    public void saveTag_dialogClosedBeforeTheWriteStarts_stillSaves() {
        AtomicBoolean ran = new AtomicBoolean();
        Tag tag = new Tag().create("Work");
        when(dataManager.addTag(tag)).thenReturn(flagging(ran));
        NameTagDialogPresenter presenter =
                new NameTagDialogPresenter(queuedIo(), new CompositeDisposable(), dataManager);
        presenter.attachView(nameView);

        presenter.saveTag(tag);
        presenter.detachView();
        io.triggerActions();

        org.junit.Assert.assertTrue(ran.get());
    }

    @Test
    public void renameTag_dialogClosedBeforeTheWriteStarts_stillRenames() {
        AtomicBoolean ran = new AtomicBoolean();
        Tag tag = new Tag().create("Work");
        when(dataManager.renameTag(tag, "Home")).thenReturn(flagging(ran));
        NameTagDialogPresenter presenter =
                new NameTagDialogPresenter(queuedIo(), new CompositeDisposable(), dataManager);
        presenter.attachView(nameView);

        presenter.editNameTag("Home", tag);
        presenter.detachView();
        io.triggerActions();

        org.junit.Assert.assertTrue(ran.get());
    }
}
