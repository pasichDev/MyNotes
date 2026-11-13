package com.pasich.mynotes.ui.presenter;

import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.contract.BackupContract;
import com.pasich.mynotes.utils.backup.BackupCacheHelper;
import com.pasich.mynotes.utils.backup.models.JsonBackup;
import com.pasich.mynotes.utils.backup.models.googleKeep.GoogleKeepImportResult;
import com.pasich.mynotes.utils.backup.otherApp.GoogleKeepImportService;
import com.pasich.mynotes.utils.constants.CloudErrors;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import dagger.hilt.android.scopes.ActivityScoped;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableSingleObserver;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/** Presenter backing the backup and restore screen. */
@ActivityScoped
public class BackupPresenter extends BasePresenter<BackupContract.view>
        implements BackupContract.presenter {

    private static final String TAG = "BackupPresenter";

    /**
     * Saves and restores outlive the screen that started them.
     *
     * <p>A presenter lives exactly as long as its activity, and a rotation in the middle of a
     * restore used to dispose it part-way, silently. The work now runs to the end and reports to
     * whichever presenter is attached at that moment. A result that finds no screen is kept only
     * for the screen that started the work, recognised by its {@link #screenToken} after a
     * recreation, and dropped once that screen is finished: it used to wait in a static for the
     * life of the process and pop up on any later visit, hours on. Main thread only.
     */
    private static final class BackgroundWork {
        static boolean restoreRunning;
        static boolean saveRunning;
        @Nullable static String restoreOwner;
        @Nullable static String saveOwner;
        @Nullable static Integer undeliveredRestore;
        @Nullable static String undeliveredRestoreOwner;
        @Nullable static Boolean undeliveredSave;
        @Nullable static String undeliveredSaveOwner;
        @Nullable static BackupPresenter current;
    }

    /**
     * Identifies the screen instance across recreations; the activity keeps it in its saved state.
     */
    private String screenToken = UUID.randomUUID().toString();

    @Inject public BackupCacheHelper serviceCache;
    @Inject GoogleKeepImportService importService;

    /** Null in unit tests, where the data manager is a mock; then nothing is transactional. */
    @Inject @Nullable AppDatabase appDatabase;

    /** The scan of another app's export, cancelled when the user closes its dialog. */
    @Nullable private Disposable otherAppScan;

    @Inject
    public BackupPresenter(
            SchedulerProvider schedulerProvider,
            CompositeDisposable compositeDisposable,
            DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }

    @Override
    public void restoreScreenToken(@Nullable String token) {
        if (token != null) {
            screenToken = token;
        }
    }

    @Override
    @NonNull
    public String getScreenToken() {
        return screenToken;
    }

    @Override
    public void onScreenFinished() {
        if (screenToken.equals(BackgroundWork.restoreOwner)) {
            BackgroundWork.restoreOwner = null;
        }
        if (screenToken.equals(BackgroundWork.saveOwner)) {
            BackgroundWork.saveOwner = null;
        }
        if (screenToken.equals(BackgroundWork.undeliveredRestoreOwner)) {
            BackgroundWork.undeliveredRestore = null;
            BackgroundWork.undeliveredRestoreOwner = null;
        }
        if (screenToken.equals(BackgroundWork.undeliveredSaveOwner)) {
            BackgroundWork.undeliveredSave = null;
            BackgroundWork.undeliveredSaveOwner = null;
        }
    }

    @Override
    public void viewIsReady() {
        BackgroundWork.current = this;
        getView().initActivity();
        getView().initListeners();
        catchUpOnBackgroundWork();
    }

    @Override
    public void detachView() {
        if (BackgroundWork.current == this) {
            BackgroundWork.current = null;
        }
        super.detachView();
    }

    /**
     * Save backup data algorithm and navigator
     *
     * @param local - check repository
     */
    @Override
    public void saveBackupPresenter(boolean local) {
        getCompositeDisposable()
                .add(
                        Single.fromCallable(JsonBackup::new)
                                .flatMap(jsonBackupTemp ->
                                        Flowable.zip(
                                                        getDataManager().getNotes(),
                                                        getDataManager().getNotesInTrash(),
                                                        getDataManager().getTagsUser(),
                                                        (noteList, trashNoteList, tagList) -> {
                                                            jsonBackupTemp.setNotes(noteList);
                                                            jsonBackupTemp.setNewTrashNotes(
                                                                    trashNoteList);
                                                            jsonBackupTemp.setTags(tagList);
                                                            return noteList.size()
                                                                    + trashNoteList.size()
                                                                    + tagList.size();
                                                        })
                                                .firstOrError()
                                                .map(countData -> {
                                                    if (countData != 0) {
                                                        jsonBackupTemp.setPreferences(
                                                                getDataManager()
                                                                        .getListPreferences());
                                                    }
                                                    return new AbstractMap.SimpleEntry<>(
                                                            jsonBackupTemp, countData);
                                                }))
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        result -> {
                                            JsonBackup jsonBackup = result.getKey();
                                            Integer countData = result.getValue();

                                            if (countData != 0) {
                                                serviceCache.setJsonBackup(jsonBackup);
                                                getView().openIntentSaveBackup(jsonBackup);
                                            } else {
                                                getView().emptyDataToBackup();
                                            }
                                        },
                                        throwable -> Log.e("RxError", "Error: ", throwable)));
    }

    /**
     * Save local backup (3/3) - write appData to public file.
     *
     * <p>Off the main thread: the archive packs every attachment and used to be built and copied on
     * the main thread from the activity-result callback. Like a restore it is not tied to this
     * screen's lifetime; a rotation would otherwise interrupt the write half way through.
     */
    @Override
    public void writeFileBackupLocal(Uri mUri) {
        if (BackgroundWork.saveRunning) {
            return;
        }
        BackgroundWork.saveRunning = true;
        BackgroundWork.saveOwner = screenToken;
        Disposable ignored =
                Single.fromCallable(() -> getDataManager().writeBackupLocalFile(serviceCache, mUri))
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(
                                BackupPresenter::finishSave,
                                throwable -> {
                                    Log.e(TAG, "Writing the backup failed", throwable);
                                    finishSave(false);
                                });
    }

    /**
     * Restore local backup (3/3) - load public file and write data.
     *
     * <p>The file is read off the main thread; it can be an archive of every attachment, which used
     * to be read whole into memory on the main thread just to look at its first two bytes.
     */
    @Override
    public void readFileBackupLocal(Uri mUri) {
        if (BackgroundWork.restoreRunning) {
            // One restore at a time; the running one reports to whichever screen is current. The
            // newly picked backup is not restored, so say so instead of dropping it silently
            // behind a progress dialog that belongs to the other restore.
            getView().showProcessRestoreDialog();
            rejectRestoreWhileRunning();
            return;
        }
        getView().showProcessRestoreDialog();
        BackgroundWork.restoreRunning = true;
        BackgroundWork.restoreOwner = screenToken;

        Disposable ignored =
                Completable.timer(3, TimeUnit.SECONDS, getSchedulerProvider().io())
                        .andThen(
                                Single.fromCallable(
                                        () -> getDataManager().readBackupLocalFile(mUri)))
                        .flatMapCompletable(jsonBackup ->
                                jsonBackup.isError()
                                        ? Completable.error(
                                                new IllegalStateException(
                                                        "The backup could not be read"))
                                        : restoreCompletable(jsonBackup))
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(
                                () -> finishRestore(CloudErrors.OKAY_RESTORE),
                                throwable -> {
                                    Log.e(TAG, "Restore failed", throwable);
                                    finishRestore(CloudErrors.BACKUP_DESTROY);
                                });
    }

    /**
     * Writes restored data, reporting to whichever backup screen is current when it ends.
     *
     * @param jsonBackup - data restore
     */
    private void restoreData(JsonBackup jsonBackup) {
        if (BackgroundWork.restoreRunning) {
            rejectRestoreWhileRunning();
            return;
        }
        BackgroundWork.restoreRunning = true;
        BackgroundWork.restoreOwner = screenToken;
        Disposable ignored =
                restoreCompletable(jsonBackup)
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(
                                () -> finishRestore(CloudErrors.OKAY_RESTORE),
                                throwable -> {
                                    Log.e(TAG, "Restore failed", throwable);
                                    finishRestore(CloudErrors.BACKUP_DESTROY);
                                });
    }

    /**
     * Writes notes, tags and trash as one transaction, then the preferences.
     *
     * <p>They used to be three transactions merged together and tied to the screen's disposables: a
     * rotation part-way through left the notes restored and the tags or the trash not, without a
     * word. Now either all of the data lands or none of it, and the preferences only once it has.
     * The work is deliberately not added to the screen's disposables.
     */
    private Completable restoreCompletable(JsonBackup jsonBackup) {
        return Completable.fromAction(() -> {
            if (jsonBackup.getTags() != null) {
                for (Tag tag : jsonBackup.getTags()) {
                    if (tag.getPosition() == 0 && tag.getSystemAction() == 0) {
                        tag.setPosition(-1);
                    }
                }
            }
            runInTransaction(() -> {
                getDataManager().addNotes(jsonBackup.getNotes()).blockingAwait();
                getDataManager().addTags(jsonBackup.getTags()).blockingAwait();
                getDataManager().addNotes(jsonBackup.getTrashNotesUnified()).blockingAwait();
            });
            getDataManager().setListPreferences(jsonBackup.getPreferences());
        });
    }

    /** Tells the user a second restore was not started because one is already running. */
    private void rejectRestoreWhileRunning() {
        runOnView(view -> view.showErrorsText(0, R.string.restore_already_running));
    }

    private void runInTransaction(Runnable body) {
        if (appDatabase != null) {
            appDatabase.runInTransaction(body);
        } else {
            body.run();
        }
    }

    private static void finishRestore(int infoCode) {
        BackgroundWork.restoreRunning = false;
        String owner = BackgroundWork.restoreOwner;
        BackgroundWork.restoreOwner = null;
        BackupPresenter current = BackgroundWork.current;
        if (current != null && current.isViewAttached()) {
            current.deliverRestore(infoCode);
        } else if (owner != null) {
            BackgroundWork.undeliveredRestore = infoCode;
            BackgroundWork.undeliveredRestoreOwner = owner;
        }
    }

    private void deliverRestore(int infoCode) {
        if (infoCode == CloudErrors.OKAY_RESTORE) {
            getView().onRestoreSuccessFlag();
        }
        getView().restoreFinish(infoCode);
    }

    private static void finishSave(boolean saved) {
        BackgroundWork.saveRunning = false;
        String owner = BackgroundWork.saveOwner;
        BackgroundWork.saveOwner = null;
        BackupPresenter current = BackgroundWork.current;
        if (current != null && current.isViewAttached()) {
            current.getView().createLocalCopyFinish(saved);
        } else if (owner != null) {
            BackgroundWork.undeliveredSave = saved;
            BackgroundWork.undeliveredSaveOwner = owner;
        }
    }

    /**
     * Shows a running restore's progress, and hands a recreation of the screen that started some
     * work what finished without it. Another screen instance never gets someone else's result.
     */
    private void catchUpOnBackgroundWork() {
        if (BackgroundWork.restoreRunning) {
            getView().showProcessRestoreDialog();
        } else if (BackgroundWork.undeliveredRestore != null
                && screenToken.equals(BackgroundWork.undeliveredRestoreOwner)) {
            int infoCode = BackgroundWork.undeliveredRestore;
            BackgroundWork.undeliveredRestore = null;
            BackgroundWork.undeliveredRestoreOwner = null;
            deliverRestore(infoCode);
        }
        if (BackgroundWork.undeliveredSave != null
                && screenToken.equals(BackgroundWork.undeliveredSaveOwner)) {
            boolean saved = BackgroundWork.undeliveredSave;
            BackgroundWork.undeliveredSave = null;
            BackgroundWork.undeliveredSaveOwner = null;
            getView().createLocalCopyFinish(saved);
        }
    }

    /**
     * Restore data algorithm and navigator
     *
     * @param local - check repository
     */
    @Override
    public void restoreBackupPresenter(boolean local) {
        getDataManager()
                .getCountData()
                .subscribeOn(getSchedulerProvider().io())
                .observeOn(getSchedulerProvider().ui())
                .subscribe(
                        new DisposableSingleObserver<>() {
                            @Override
                            public void onSuccess(Integer countData) {
                                if (countData == 0) {
                                    if (local) {
                                        getView().openIntentReadBackup();
                                    } else {
                                        return;
                                    }
                                } else {
                                    getView().dialogRestoreData(local);
                                }

                                dispose();
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e("RxError", "Error: ", e);
                            }
                        });
    }

    /** Export all notes data */
    @Override
    public void exportAllNotesPresenter() {
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .getNotes()
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        notes -> {
                                            if (notes != null && !notes.isEmpty()) {
                                                // Sort notes by creation date - newest first
                                                notes.sort((note1, note2) ->
                                                        Long.compare(
                                                                note2.getDate(), note1.getDate()));
                                                getView().openShareOptionsDialog(notes, true);
                                            } else {
                                                getView().emptyDataToBackup();
                                            }
                                        },
                                        throwable -> {
                                            Log.e(
                                                    "BackupPresenter",
                                                    "Error loading notes for export",
                                                    throwable);
                                            getView().restoreFinish(CloudErrors.BACKUP_DESTROY);
                                        }));
    }

    @Override
    public void importFromZipOtherApp(Uri fileUri) {
        cancelImportFromZipOtherApp();
        otherAppScan =
                Single.fromCallable(() -> importService.importFromZip(fileUri))
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(
                                result -> runOnView(view -> view.showImportResultOtherApp(result)),
                                throwable ->
                                        runOnView(view ->
                                                view.showImportResultOtherApp(
                                                        GoogleKeepImportResult.error(
                                                                throwable.getMessage()))));
        getCompositeDisposable().add(otherAppScan);
    }

    @Override
    public void cancelImportFromZipOtherApp() {
        if (otherAppScan != null) {
            getCompositeDisposable().remove(otherAppScan); // remove() also disposes it
            otherAppScan = null;
        }
    }

    @Override
    public void importDataOtherApp(GoogleKeepImportResult result) {
        getCompositeDisposable()
                .add(
                        Flowable.zip(
                                        getDataManager().getNotes().firstOrError().toFlowable(),
                                        getDataManager()
                                                .getNotesInTrash()
                                                .firstOrError()
                                                .toFlowable(),
                                        getDataManager().getTagsUser().firstOrError().toFlowable(),
                                        (existingNotes, existingTrashNotes, existingTags) -> {

                                            // Фільтруємо нотатки по вмісту
                                            List<Note> newNotes = new ArrayList<>();
                                            for (Note note : result.toAppNotes()) {
                                                boolean duplicate = false;
                                                for (Note existing : existingNotes) {
                                                    if (note.getTitle().equals(existing.getTitle())
                                                            && note.getValue()
                                                                    .equals(existing.getValue())
                                                            && note.getDate()
                                                                    == existing.getDate()) {
                                                        duplicate = true;
                                                        break;
                                                    }
                                                }
                                                if (!duplicate) newNotes.add(note);
                                            }

                                            // Фільтруємо видалені нотатки по вмісту
                                            List<Note> newTrashNotes = new ArrayList<>();
                                            for (Note trash : result.toAppTrashedNotes()) {
                                                boolean duplicate = false;
                                                for (Note existing : existingTrashNotes) {
                                                    if (trash.getTitle().equals(existing.getTitle())
                                                            && trash.getValue()
                                                                    .equals(existing.getValue())
                                                            && trash.getDate()
                                                                    == existing.getDate()) {
                                                        duplicate = true;
                                                        break;
                                                    }
                                                }
                                                if (!duplicate) newTrashNotes.add(trash);
                                            }

                                            // Фільтруємо теги по назві
                                            List<Tag> newTags = new ArrayList<>();
                                            for (Tag tag : result.toAppTags()) {
                                                boolean duplicate = false;
                                                for (Tag existing : existingTags) {
                                                    if (tag.getNameTag()
                                                            .equals(existing.getNameTag())) {
                                                        duplicate = true;
                                                        break;
                                                    }
                                                }
                                                if (!duplicate) newTags.add(tag);
                                            }

                                            // Якщо нічого немає для імпорту — викидаємо помилку
                                            if (newNotes.isEmpty()
                                                    && newTrashNotes.isEmpty()
                                                    && newTags.isEmpty()) {
                                                throw new Exception("No new data to import");
                                            }

                                            JsonBackup jsonBackup = new JsonBackup();
                                            jsonBackup.setNotes(newNotes);
                                            jsonBackup.setNewTrashNotes(newTrashNotes);
                                            jsonBackup.setTags(newTags);

                                            return jsonBackup;
                                        })
                                .subscribeOn(getSchedulerProvider().computation())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        this::restoreData,
                                        throwable ->
                                                runOnView(view ->
                                                        view.showErrorsText(
                                                                0, R.string.empty_data_import))));
    }

    @Override
    public void startProcessSelectedFileOtherApp(Uri fileUri) {
        getView().processSelectedFileOtherApp(fileUri);
    }
}
