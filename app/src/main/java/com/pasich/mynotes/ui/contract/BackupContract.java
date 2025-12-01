package com.pasich.mynotes.ui.contract;


import android.net.Uri;

import androidx.annotation.StringRes;

import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.utils.backup.models.JsonBackup;
import com.pasich.mynotes.utils.backup.models.googleKeep.GoogleKeepImportResult;

import dagger.hilt.android.scopes.ActivityScoped;

public interface BackupContract {

    interface view extends BaseView {

        void initActivity();

        void openIntentSaveBackup(JsonBackup jsonBackup);

        void openIntentReadBackup();

        void dialogRestoreData(boolean local);

        void restoreFinish(int infoCode);

        void showProcessRestoreDialog();

        void emptyDataToBackup();

        void createLocalCopyFinish(boolean error);

        void showErrorsText(int errorCode, @StringRes int string);

        void openShareOptionsDialog(java.util.List<Note> notes, boolean isDataExport);

        void showImportResultOtherApp(GoogleKeepImportResult result);

        void setupImportCallbackOtherApp(GoogleKeepImportResult result);

        void processSelectedFileOtherApp(Uri fileUri);
    }


    @ActivityScoped
    interface presenter extends BasePresenter<view> {

        void saveBackupPresenter(boolean local);

        void restoreBackupPresenter(boolean local);

        void writeFileBackupLocal(Uri mUri);

        void readFileBackupLocal(Uri mUri);

        void exportAllNotesPresenter();

        void importDataOtherApp(GoogleKeepImportResult result);

        void importFromZipOtherApp(Uri fileUri);

        void startProcessSelectedFileOtherApp(Uri fileUri);

    }
}
