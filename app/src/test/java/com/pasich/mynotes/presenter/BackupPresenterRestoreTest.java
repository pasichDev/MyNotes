package com.pasich.mynotes.presenter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;
import com.google.gson.Gson;
import com.pasich.mynotes.R;
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
        return presenter(view, null);
    }

    /** A screen instance; a non-null token makes it the recreation of the one that saved it. */
    private BackupPresenter presenter(BackupContract.view view, String savedToken) {
        BackupPresenter presenter =
                new BackupPresenter(schedulers(), new CompositeDisposable(), dataManager);
        presenter.attachView(view);
        presenter.restoreScreenToken(savedToken);
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
    public void restore_finishingWithNoScreen_isReportedToItsRecreation() {
        BackupContract.view first = mock(BackupContract.view.class);
        BackupContract.view recreated = mock(BackupContract.view.class);

        BackupPresenter gone = presenter(first);
        String token = gone.getScreenToken();
        gone.importDataOtherApp(oneKeepNote());
        gone.detachView();
        io.triggerActions();

        presenter(recreated, token);

        verify(recreated).restoreFinish(CloudErrors.OKAY_RESTORE);
        verify(recreated).onRestoreSuccessFlag();
    }

    @Test
    public void restore_finishingWithNoScreen_isNotReportedToAnUnrelatedVisit() {
        BackupContract.view first = mock(BackupContract.view.class);
        BackupContract.view later = mock(BackupContract.view.class);
        BackupContract.view recreated = mock(BackupContract.view.class);

        BackupPresenter gone = presenter(first);
        String token = gone.getScreenToken();
        gone.importDataOtherApp(oneKeepNote());
        gone.detachView();
        io.triggerActions();

        // Another instance of the screen does not get it...
        BackupPresenter unrelated = presenter(later);
        unrelated.detachView();
        verify(later, never()).restoreFinish(anyInt());
        verify(later, never()).onRestoreSuccessFlag();
        // ...and the recreation of the starting screen still does.
        presenter(recreated, token);
        verify(recreated).restoreFinish(CloudErrors.OKAY_RESTORE);
    }

    @Test
    public void restore_ofAFinishedScreen_isDroppedNotReportedLater() {
        BackupContract.view first = mock(BackupContract.view.class);
        BackupContract.view later = mock(BackupContract.view.class);

        BackupPresenter gone = presenter(first);
        String token = gone.getScreenToken();
        gone.importDataOtherApp(oneKeepNote());
        gone.onScreenFinished();
        gone.detachView();
        io.triggerActions();

        presenter(later, token);

        verify(later, never()).restoreFinish(anyInt());
        verify(later, never()).onRestoreSuccessFlag();
    }

    @Test
    public void save_finishingWithNoScreen_reachesOnlyItsRecreation() {
        BackupContract.view first = mock(BackupContract.view.class);
        BackupContract.view later = mock(BackupContract.view.class);
        BackupContract.view recreated = mock(BackupContract.view.class);

        BackupPresenter gone = presenter(first);
        String token = gone.getScreenToken();
        gone.writeFileBackupLocal(null);
        gone.detachView();
        io.triggerActions();

        presenter(later).detachView();
        verify(later, never()).createLocalCopyFinish(anyBoolean());
        presenter(recreated, token);
        verify(recreated).createLocalCopyFinish(anyBoolean());
    }

    @Test
    public void pickingASaveTargetWhileASaveRuns_removesTheEmptyFileAndTellsTheUser() {
        BackupContract.view view = mock(BackupContract.view.class);
        BackupPresenter presenter = presenter(view);
        Uri running = mock(Uri.class);
        Uri refused = mock(Uri.class);
        presenter.writeFileBackupLocal(running);

        presenter.writeFileBackupLocal(refused);

        verify(view).discardUnwrittenBackupFile(refused);
        verify(view).showErrorsText(0, R.string.save_already_running);
        verify(view, never()).discardUnwrittenBackupFile(running);
        io.triggerActions();
        verify(dataManager).writeBackupLocalFile(any(), eq(running));
        verify(dataManager, never()).writeBackupLocalFile(any(), eq(refused));
        verify(view).createLocalCopyFinish(anyBoolean());
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

    @Test
    public void pickingABackupWhileARestoreRuns_tellsTheUserInsteadOfDroppingIt() {
        BackupContract.view view = mock(BackupContract.view.class);
        BackupPresenter presenter = presenter(view);
        presenter.importDataOtherApp(oneKeepNote());

        presenter.readFileBackupLocal(null);

        verify(view).showErrorsText(0, R.string.restore_already_running);
        verify(dataManager, never()).readBackupLocalFile(any());
        io.triggerActions();
        verify(view).restoreFinish(CloudErrors.OKAY_RESTORE);
    }

    @Test
    public void secondKeepImportWhileARestoreRuns_tellsTheUser() {
        BackupContract.view view = mock(BackupContract.view.class);
        BackupPresenter presenter = presenter(view);
        presenter.importDataOtherApp(oneKeepNote());

        presenter.importDataOtherApp(oneKeepNote());

        verify(view).showErrorsText(0, R.string.restore_already_running);
        io.triggerActions();
        verify(view).restoreFinish(CloudErrors.OKAY_RESTORE);
    }
}
