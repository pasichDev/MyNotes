package com.pasich.mynotes.presenter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.pasich.mynotes.base.BasePresenterTest;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.ui.contract.BackupContract;
import com.pasich.mynotes.ui.presenter.BackupPresenter;
import com.pasich.mynotes.utils.backup.models.googleKeep.GoogleKeepImportResult;
import com.pasich.mynotes.utils.backup.models.googleKeep.GoogleKeepNote;
import com.pasich.mynotes.utils.constants.CloudErrors;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.schedulers.TestScheduler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/** A restore is not cut short by the screen that started it going away. */
public class BackupPresenterRestoreTest extends BasePresenterTest {

    private final TestScheduler io = new TestScheduler();
    private DataManager dataManager;

    @Before
    public void setUp() {
        dataManager = mock(DataManager.class);
        when(dataManager.getNotes()).thenReturn(Flowable.just(new ArrayList<>()));
        when(dataManager.getNotesInTrash()).thenReturn(Flowable.just(new ArrayList<>()));
        when(dataManager.getTagsUser()).thenReturn(Flowable.just(new ArrayList<>()));
        when(dataManager.addNotes(anyList())).thenReturn(Completable.complete());
        when(dataManager.addTags(anyList())).thenReturn(Completable.complete());
    }

    private SchedulerProvider schedulers() {
        return new SchedulerProvider() {
            @Override
            public Scheduler ui() {
                return Schedulers.trampoline();
            }

            @Override
            public Scheduler computation() {
                return Schedulers.trampoline();
            }

            @Override
            public Scheduler io() {
                return io;
            }
        };
    }

    private BackupPresenter presenter(BackupContract.view view) {
        BackupPresenter presenter =
                new BackupPresenter(schedulers(), new CompositeDisposable(), dataManager);
        presenter.attachView(view);
        presenter.viewIsReady();
        return presenter;
    }

    private static GoogleKeepImportResult oneKeepNote() {
        GoogleKeepNote note =
                new Gson()
                        .fromJson(
                                "{\"title\":\"t\",\"textContent\":\"body\"}", GoogleKeepNote.class);
        return GoogleKeepImportResult.success(
                List.of(note), Collections.emptyList(), Collections.emptyList());
    }

    @Test
    public void restore_reportsToTheScreenThatIsCurrentWhenItEnds() {
        BackupContract.view first = mock(BackupContract.view.class);
        BackupContract.view second = mock(BackupContract.view.class);

        BackupPresenter rotatedAway = presenter(first);
        rotatedAway.importDataOtherApp(oneKeepNote());
        // A rotation: the old screen goes, a new one comes while the restore is still queued.
        rotatedAway.detachView();
        presenter(second);
        verify(second).showProcessRestoreDialog();

        io.triggerActions();

        verify(dataManager, org.mockito.Mockito.times(2)).addNotes(anyList());
        verify(dataManager).addTags(anyList());
        verify(second).onRestoreSuccessFlag();
        verify(second).restoreFinish(CloudErrors.OKAY_RESTORE);
        verify(first, never()).restoreFinish(anyInt());
    }

    @Test
    public void restore_finishingWithNoScreen_isReportedToTheNextOne() {
        BackupContract.view first = mock(BackupContract.view.class);
        BackupContract.view later = mock(BackupContract.view.class);

        BackupPresenter gone = presenter(first);
        gone.importDataOtherApp(oneKeepNote());
        gone.detachView();
        io.triggerActions();

        presenter(later);

        verify(later).restoreFinish(CloudErrors.OKAY_RESTORE);
    }

    @Test
    public void failedRestore_isReportedNotSwallowed() {
        when(dataManager.addTags(anyList()))
                .thenReturn(Completable.error(new IllegalStateException("disk full")));
        BackupContract.view view = mock(BackupContract.view.class);

        presenter(view).importDataOtherApp(oneKeepNote());
        io.triggerActions();

        verify(view).restoreFinish(CloudErrors.BACKUP_DESTROY);
        verify(view, never()).onRestoreSuccessFlag();
        verify(dataManager, never()).setListPreferences(any());
    }
}
