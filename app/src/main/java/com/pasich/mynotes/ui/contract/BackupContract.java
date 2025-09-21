package com.pasich.mynotes.ui.contract;


import android.net.Uri;

import androidx.annotation.StringRes;

import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.services.drive.Drive;
import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.backup.JsonBackup;
import com.pasich.mynotes.data.model.backup.googleKeep.GoogleKeepImportResult;
import com.pasich.mynotes.utils.backup.BackupCacheHelper;

import dagger.hilt.android.scopes.ActivityScoped;

public interface BackupContract {

    interface view extends BaseView {

        void initActivity();

        void initConnectAccount();

        void startIntentLogInUserCloud();

        void loadingLastBackupInfoCloud();

        void openIntentSaveBackup(JsonBackup jsonBackup);

        void openIntentReadBackup();

        void dialogChoiceVariantAutoBackup();

        void dialogRestoreData(boolean local);

        void restoreFinish(int infoCode);

        void showProcessRestoreDialog();

        void emptyDataToBackup();

        void createLocalCopyFinish(boolean error);

        boolean showErrors(int errorCode);

        void showErrorsText(int errorCode, @StringRes int string);

        void editLastDataEditBackupCloud(long lastDate, boolean error);

        void startWriteBackupCloud(JsonBackup jsonBackup);

        void startReadBackupCloud();

        void visibleProgressBarCLoud();

        void goneProgressBarCLoud();

        void getClickedOffUpdate();

        MediaHttpUploaderProgressListener getProcessListener();
        
        void openShareOptionsDialog(java.util.List<Note> notes, boolean isDataExport);

        void showImportResultOtherApp(GoogleKeepImportResult result);

        void setupImportCallbackOtherApp(GoogleKeepImportResult result);

        void processSelectedFileOtherApp(Uri fileUri);
    }


    @ActivityScoped
    interface presenter extends BasePresenter<view> {

        void clickInformationCloud(boolean isAuth);

        void saveBackupPresenter(boolean local);

        void restoreBackupPresenter(boolean local);

        void writeFileBackupLocal(BackupCacheHelper serviceCache, Uri mUri);

        void openChoiceDialogAutoBackup();

        void readFileBackupLocal(Uri mUri);

        void writeFileBackupCloud(Drive mDriveCredential, JsonBackup jsonBackup);

        void readFileBackupCloud(Drive mDriveCredential);

        void saveDataLoadingLastBackup(Drive mDriveCredential);
        
        void exportAllNotesPresenter();

        void importDataOtherApp(GoogleKeepImportResult result);

        void importFromZipOtherApp(Uri fileUri);

        void startProcessSelectedFileOtherApp(Uri fileUri);

    }
}
