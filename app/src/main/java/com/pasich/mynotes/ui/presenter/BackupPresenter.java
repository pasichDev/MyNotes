package com.pasich.mynotes.ui.presenter;

import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.ARGUMENT_LAST_BACKUP_ID;
import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.ARGUMENT_LAST_BACKUP_TIME;

import android.net.Uri;
import android.util.Log;

import com.google.api.services.drive.Drive;
import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.backup.JsonBackup;
import com.pasich.mynotes.ui.contract.BackupContract;
import com.pasich.mynotes.utils.backup.BackupCacheHelper;
import com.pasich.mynotes.utils.constants.CloudErrors;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableSingleObserver;

import java.util.AbstractMap;

@ActivityScoped
public class BackupPresenter extends BasePresenter<BackupContract.view> implements BackupContract.presenter {

    private int clickUpdate = 0;

    @Inject
    public BackupPresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }

    @Override
    public void viewIsReady() {
        getView().initActivity();
        getView().initConnectAccount();
        getView().initListeners();
    }

    @Override
    public void detachView() {
        super.detachView();
    }


    @Override
    public void openChoiceDialogAutoBackup() {
        getView().dialogChoiceVariantAutoBackup();
    }


    /**
     * Click to layout drive info
     *
     * @param isAuth - check auth user
     */
    @Override
    public void clickInformationCloud(boolean isAuth) {
        if (isAuth) {
            if (clickUpdate < 2) {
                getView().loadingLastBackupInfoCloud();
                clickUpdate = clickUpdate + 1;
                if (clickUpdate >= 2) getView().getClickedOffUpdate();
            }
        } else {
            getView().startIntentLogInUserCloud();
        }
    }

    /**
     * Save backup data algorithm and navigator
     * Оптимізовано для великих об'ємів даних
     *
     * @param local - check repository
     */
    @Override
    public void saveBackupPresenter(boolean local) {
        getCompositeDisposable().add(
            Single.fromCallable(JsonBackup::new)
            .flatMap(jsonBackupTemp -> 
                Flowable.zip(
                    getDataManager().getNotes(),
                    getDataManager().getTrashNotesLoad(), 
                    getDataManager().getTagsUser(), 
                    (noteList, trashNoteList, tagList) -> {
                        jsonBackupTemp.setNotes(noteList);
                        jsonBackupTemp.setTrashNotes(trashNoteList);
                        jsonBackupTemp.setTags(tagList);
                        return noteList.size() + trashNoteList.size() + tagList.size();
                    }
                ).firstOrError()
                .map(countData -> {
                    if (countData != 0) {
                        jsonBackupTemp.setPreferences(getDataManager().getListPreferences());
                    }
                    return new AbstractMap.SimpleEntry<>(jsonBackupTemp, countData);
                })
            )
            .subscribeOn(getSchedulerProvider().io())
            .observeOn(getSchedulerProvider().ui())
            .subscribe(result -> {
                JsonBackup jsonBackup = result.getKey();
                Integer countData = result.getValue();
                
                if (countData != 0) {
                    if (local) {
                        getView().openIntentSaveBackup(jsonBackup);
                    } else {
                        getView().startWriteBackupCloud(jsonBackup);
                    }
                } else {
                    getView().emptyDataToBackup();
                }
            },
            throwable -> Log.e("RxError", "Error: ", throwable))
        );
    }


    /**
     * Save local backup (3/3) - write appData to public file
     */
    @Override
    public void writeFileBackupLocal(BackupCacheHelper serviceCache, Uri mUri) {
        getView().createLocalCopyFinish(getDataManager().writeBackupLocalFile(serviceCache, mUri));
    }


    /**
     * Restore local backup (3/3) - load public file and write data
     */
    @Override
    public void readFileBackupLocal(Uri mUri) {
        getView().showProcessRestoreDialog();
        final JsonBackup jsonBackup = getDataManager().readBackupLocalFile(mUri);
        if (jsonBackup.isError()) {
            getView().restoreFinish(CloudErrors.BACKUP_DESTROY);
        } else {
            restoreData(jsonBackup);
        }
    }

    /**
     * Restore date request rxJava
     *
     * @param jsonBackup - data restore
     */
    private void restoreData(JsonBackup jsonBackup) {
        // Проверяем и модифицируем теги из старых резервных копий
        // чтобы поле position было корректным
        if (jsonBackup.getTags() != null && !jsonBackup.getTags().isEmpty()) {
             for (int i = 0; i < jsonBackup.getTags().size(); i++) {
          Tag tag = jsonBackup.getTags().get(i);
                // Если это старая резервная копия, где position не было установлено правильно
                // (оставлен 0 по умолчанию, хотя в конструкторе create() устанавливается -1)
                if (tag.getPosition() == 0 && tag.getSystemAction() == 0) {
                    // Устанавливаем position в -1 для пользовательских тегов, как в методе create()
                    tag.setPosition(-1);
                }
            }
        }
        
        getCompositeDisposable().add(
            Completable.fromAction(() -> getDataManager().setListPreferences(jsonBackup.getPreferences())).subscribeOn(getSchedulerProvider().io())
            .andThen(
                    Completable.mergeArray(
                            getDataManager().addNotes(jsonBackup.getNotes()),
                            getDataManager().addTags(jsonBackup.getTags()),
                            getDataManager().addTrashNotes(jsonBackup.getTrashNotes())
                    )
            )
            .subscribeOn(getSchedulerProvider().io())
            .observeOn(getSchedulerProvider().ui())
            .subscribe(
                () -> getView().restoreFinish(CloudErrors.OKAY_RESTORE),
                throwable -> {
                    Log.e("BackupRestore", "Error restore: " + throwable.getMessage(), throwable);
                    getView().showErrors(CloudErrors.BACKUP_DESTROY);
                }
            )
        );
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
                        getView().startReadBackupCloud();
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
     * Upload backup to cloud (3/3)
     *
     * @param mDriveCredential - check isAuth user
     * @param jsonBackup       - model data backup
     */
    @Override
    public void writeFileBackupCloud(Drive mDriveCredential, JsonBackup jsonBackup) {
        getView().visibleProgressBarCLoud();
        final java.io.File backupTemp = getDataManager().writeTempBackup(jsonBackup);
        if (backupTemp == null) {
            getView().showErrors(CloudErrors.ERROR_CREATE_CLOUD_BACKUP);
        } else {
            getDataManager().getOldBackupForCLean(mDriveCredential) //load old backup
                    .onSuccessTask(listBackups -> getDataManager().writeCloudBackup(mDriveCredential, backupTemp, getView().getProcessListener())//write new backup
                            .addOnCompleteListener(stack -> getView().goneProgressBarCLoud()).addOnSuccessListener(backupCloud -> {
                                getView().editLastDataEditBackupCloud(backupCloud.getLastDate(), false);
                                getDataManager().getBackupCloudInfoPreference().putString(ARGUMENT_LAST_BACKUP_ID, backupCloud.getId()).putLong(ARGUMENT_LAST_BACKUP_TIME, backupCloud.getLastDate());
                                getView().createLocalCopyFinish(true);

                            }).onSuccessTask(backupCloud -> getDataManager().cleanOldBackups(mDriveCredential, listBackups)).addOnFailureListener(stack -> getView().showErrors(CloudErrors.NETWORK_FALSE))).addOnFailureListener(stack -> {
                        getView().goneProgressBarCLoud();
                        getView().showErrors(CloudErrors.NETWORK_FALSE);
                    }).addOnCompleteListener(task -> backupTemp.delete());

        }
    }

    /**
     * Load restore backup to cloud (3/3)
     *
     * @param mDriveCredential - check isAuth user
     */
    @Override
    public void readFileBackupCloud(Drive mDriveCredential) {
        getView().showProcessRestoreDialog();
        getDataManager().getReadLastBackupCloud(mDriveCredential).addOnSuccessListener(jsonBackup -> {

            if (jsonBackup.isError()) {
                getView().restoreFinish(CloudErrors.BACKUP_DESTROY);
            } else {
                restoreData(jsonBackup);
            }
        }).addOnFailureListener(stack -> getView().restoreFinish(CloudErrors.NETWORK_ERROR));
    }

    /**
     * Save data last backup cloud
     */
    @Override
    public void saveDataLoadingLastBackup(Drive mDriveCredential) {
        getDataManager().getLastBackupInfo(mDriveCredential).addOnSuccessListener(lastInfo -> {
            if (lastInfo != null) {
                if (lastInfo.getErrorCode() == 0) {
                    getDataManager().getBackupCloudInfoPreference().setString(ARGUMENT_LAST_BACKUP_ID, lastInfo.getId());
                    getDataManager().getBackupCloudInfoPreference().setLong(ARGUMENT_LAST_BACKUP_TIME, lastInfo.getLastDate());
                } else {
                    getView().showErrors(CloudErrors.LAST_BACKUP_EMPTY_DRIVE_VIEW);
                }
                getView().editLastDataEditBackupCloud(lastInfo.getLastDate(), lastInfo.getErrorCode() != 0);
            } else {
                Log.e("BackupPresenter", "LastBackupInfo is null");
                getView().showErrors(CloudErrors.ERROR_LOAD_LAST_INFO_BACKUP);
            }
        }).addOnFailureListener(stack ->
                {
                    Log.e("BackupPresenter", "Error loading last backup info: " + stack.getMessage(), stack);
                    getView().showErrors(CloudErrors.ERROR_LOAD_LAST_INFO_BACKUP);
                }


        );
    }
    
    /**
     * Export all notes data
     */
    @Override
    public void exportAllNotesPresenter() {
        getCompositeDisposable().add(getDataManager().getNotes()
                .subscribeOn(getSchedulerProvider().io())
                .observeOn(getSchedulerProvider().ui())
                .subscribe(notes -> {
                    if (notes != null && !notes.isEmpty()) {
                        // Sort notes by creation date - newest first
                        notes.sort((note1, note2) -> Long.compare(note2.getDate(), note1.getDate()));
                        getView().openShareOptionsDialog(notes, true);
                    } else {
                        getView().showErrors(CloudErrors.NETWORK_ERROR);
                    }
                }, throwable -> {
                    Log.e("BackupPresenter", "Error loading notes for export", throwable);
                    getView().showErrors(CloudErrors.NETWORK_ERROR);
                }));
    }

}
