package com.pasich.mynotes.ui.presenter;

import android.net.Uri;
import android.util.Log;

import com.pasich.mynotes.R;
import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.backup.JsonBackup;
import com.pasich.mynotes.data.model.backup.googleKeep.GoogleKeepImportResult;
import com.pasich.mynotes.ui.contract.BackupContract;
import com.pasich.mynotes.utils.backup.BackupCacheHelper;
import com.pasich.mynotes.utils.backup.otherApp.GoogleKeepImportService;
import com.pasich.mynotes.utils.constants.CloudErrors;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;

@ActivityScoped
public class BackupPresenter extends BasePresenter<BackupContract.view> implements BackupContract.presenter {

    @Inject
    public BackupCacheHelper serviceCache;
    @Inject
    GoogleKeepImportService importService;

    @Inject
    public BackupPresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }

    @Override
    public void viewIsReady() {
        getView().initActivity();
        getView().initListeners();
    }

    @Override
    public void detachView() {
        super.detachView();
    }

    /**
     * Save backup data algorithm and navigator
     *
     * @param local - check repository
     */
    @Override
    public void saveBackupPresenter(boolean local) {
        getCompositeDisposable().add(Single.fromCallable(JsonBackup::new)
                .flatMap(jsonBackupTemp -> Flowable.zip(
                        getDataManager().getNotes(), getDataManager().getNotesInTrash(),
                        getDataManager().getTagsUser(), (noteList, trashNoteList, tagList) -> {
                            jsonBackupTemp.setNotes(noteList);
                            jsonBackupTemp.setNewTrashNotes(trashNoteList);
                            jsonBackupTemp.setTags(tagList);
                            return noteList.size() + trashNoteList.size() + tagList.size();
                        }).firstOrError().map(countData -> {
                    if (countData != 0) {
                        jsonBackupTemp.setPreferences(getDataManager().getListPreferences());
                    }
                    return new AbstractMap.SimpleEntry<>(jsonBackupTemp, countData);
                })).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(result -> {
                    JsonBackup jsonBackup = result.getKey();
                    Integer countData = result.getValue();

                    if (countData != 0) {
                        serviceCache.setJsonBackup(jsonBackup);
                        getView().openIntentSaveBackup(jsonBackup);
                    } else {
                        getView().emptyDataToBackup();
                    }
                }, throwable -> Log.e("RxError", "Error: ", throwable)));

        /*  */
    }


    /**
     * Save local backup (3/3) - write appData to public file
     */
    @Override
    public void writeFileBackupLocal(Uri mUri) {
        getView().createLocalCopyFinish(getDataManager().writeBackupLocalFile(serviceCache, mUri));
    }


    /**
     * Restore local backup (3/3) - load public file and write data
     */
    @Override
    public void readFileBackupLocal(Uri mUri) {
        getView().showProcessRestoreDialog();

        getCompositeDisposable().add(
                Completable.timer(3, java.util.concurrent.TimeUnit.SECONDS)
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(() -> {
                            final JsonBackup jsonBackup = getDataManager().readBackupLocalFile(mUri);
                            if (jsonBackup.isError()) {
                                getView().restoreFinish(CloudErrors.BACKUP_DESTROY);
                            } else {
                                restoreData(jsonBackup);
                            }
                        })
        );

    }


    /**
     * Restore date request rxJava
     *
     * @param jsonBackup - data restore
     */
    private void restoreData(JsonBackup jsonBackup) {
        if (jsonBackup.getTags() != null && !jsonBackup.getTags().isEmpty()) {
            for (int i = 0; i < jsonBackup.getTags().size(); i++) {
                Tag tag = jsonBackup.getTags().get(i);
                if (tag.getPosition() == 0 && tag.getSystemAction() == 0) {
                    tag.setPosition(-1);
                }
            }
        }

        getCompositeDisposable().add(Completable.fromAction(() ->
                        getDataManager()
                                .setListPreferences(jsonBackup.getPreferences()))
                .subscribeOn(getSchedulerProvider().io())
                .andThen(Completable.mergeArray(
                        getDataManager().addNotes(jsonBackup.getNotes()),// main notes
                        getDataManager().addTags(jsonBackup.getTags()), // tags
                        getDataManager().addNotes(jsonBackup.getTrashNotesUnified()) // trash notes
                ))
                .subscribeOn(getSchedulerProvider().io())
                .observeOn(getSchedulerProvider().ui())
                .subscribe(() -> getView().restoreFinish(CloudErrors.OKAY_RESTORE), throwable -> {
                    Log.e("BackupRestore", "Error restore: " + throwable.getMessage(), throwable);
                    getView().restoreFinish(CloudErrors.BACKUP_DESTROY);
                }));
    }


    /**
     * Restore data algorithm and navigator
     *
     * @param local - check repository
     */
    @Override
    public void restoreBackupPresenter(boolean local) {
        getDataManager().getCountData().subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(new DisposableSingleObserver<>() {
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


    /**
     * Export all notes data
     */
    @Override
    public void exportAllNotesPresenter() {
        getCompositeDisposable().add(getDataManager().getNotes().subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(notes -> {
            if (notes != null && !notes.isEmpty()) {
                // Sort notes by creation date - newest first
                notes.sort((note1, note2) -> Long.compare(note2.getDate(), note1.getDate()));
                getView().openShareOptionsDialog(notes, true);
            } else {
                getView().emptyDataToBackup();
            }
        }, throwable -> {
            Log.e("BackupPresenter", "Error loading notes for export", throwable);
            getView().restoreFinish(CloudErrors.BACKUP_DESTROY);
        }));
    }

    @Override
    public void importFromZipOtherApp(Uri fileUri) {
        getCompositeDisposable().add(Single.fromCallable(() -> importService.importFromZip(fileUri)).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(result -> {
            // Після імпорту повідомляємо View про результат
            if (getView() != null) {
                getView().showImportResultOtherApp(result);
                if (!result.hasError()) {
                    getView().setupImportCallbackOtherApp(result);
                }
            }
        }, throwable -> {
            if (getView() != null) {
                getView().showImportResultOtherApp(GoogleKeepImportResult.error(throwable.getMessage()));
            }
        }));
    }

    @Override
    public void importDataOtherApp(GoogleKeepImportResult result) {
        getCompositeDisposable().add(Flowable.zip(
                getDataManager().getNotes().firstOrError().toFlowable(),
                getDataManager().getNotesInTrash().firstOrError().toFlowable(),
                getDataManager().getTagsUser().firstOrError().toFlowable(),
                (existingNotes, existingTrashNotes, existingTags) -> {

                    // Фільтруємо нотатки по вмісту
                    List<Note> newNotes = new ArrayList<>();
                    for (Note note : result.toAppNotes()) {
                        boolean duplicate = false;
                        for (Note existing : existingNotes) {
                            if (note.getTitle().equals(existing.getTitle()) && note.getValue().equals(existing.getValue()) && note.getDate() == existing.getDate()) {
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
                            if (trash.getTitle().equals(existing.getTitle()) && trash.getValue().equals(existing.getValue()) && trash.getDate() == existing.getDate()) {
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
                            if (tag.getNameTag().equals(existing.getNameTag())) {
                                duplicate = true;
                                break;
                            }
                        }
                        if (!duplicate) newTags.add(tag);
                    }

                    // Якщо нічого немає для імпорту — викидаємо помилку
                    if (newNotes.isEmpty() && newTrashNotes.isEmpty() && newTags.isEmpty()) {
                        throw new Exception("No new data to import");
                    }

                    JsonBackup jsonBackup = new JsonBackup();
                    jsonBackup.setNotes(newNotes);
                    jsonBackup.setNewTrashNotes(newTrashNotes);
                    jsonBackup.setTags(newTags);

                    return jsonBackup;
                }).subscribeOn(getSchedulerProvider().computation()).observeOn(AndroidSchedulers.mainThread()).subscribe(this::restoreData, throwable -> getView().showErrorsText(0, R.string.empty_data_import)));


    }


    @Override
    public void startProcessSelectedFileOtherApp(Uri fileUri) {
        getView().processSelectedFileOtherApp(fileUri);
    }

}
