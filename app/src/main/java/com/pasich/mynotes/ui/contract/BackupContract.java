package com.pasich.mynotes.ui.contract;

import android.net.Uri;
import androidx.annotation.StringRes;
import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.utils.backup.models.JsonBackup;
import com.pasich.mynotes.utils.backup.models.googleKeep.GoogleKeepImportResult;
import dagger.hilt.android.scopes.ActivityScoped;

/** Contract for the backup and restore screen. */
public interface BackupContract {

    interface view extends BaseView {

        /** Initialises the activity UI and listeners. */
        void initActivity();

        /** Launches the system file-picker to write a backup. */
        void openIntentSaveBackup(JsonBackup jsonBackup);

        /** Launches the system file-picker to read a backup. */
        void openIntentReadBackup();

        /** Shows the confirmation dialog before restoring data. */
        void dialogRestoreData(boolean local);

        /** Called when a restore operation completes with a result code. */
        void restoreFinish(int infoCode);

        /** Shows the in-progress dialog during restore. */
        void showProcessRestoreDialog();

        /** Notifies the user that there is no data available to back up. */
        void emptyDataToBackup();

        /** Called after a local copy write attempt; error is true on failure. */
        void createLocalCopyFinish(boolean error);

        /** Shows a localised error message identified by errorCode and string. */
        void showErrorsText(int errorCode, @StringRes int string);

        /** Opens the share sheet for the given notes list. */
        void openShareOptionsDialog(java.util.List<Note> notes, boolean isDataExport);

        /** Displays the result summary of a Google Keep import. */
        void showImportResultOtherApp(GoogleKeepImportResult result);

        /** Registers the import result callback to confirm the import. */
        void setupImportCallbackOtherApp(GoogleKeepImportResult result);

        /** Processes the user-selected file URI from another app. */
        void processSelectedFileOtherApp(Uri fileUri);

        /** Sets a flag indicating a successful restore for the calling screen. */
        void onRestoreSuccessFlag();
    }

    @ActivityScoped
    interface presenter extends BasePresenter<view> {

        /** Initiates the backup-save flow for local or cloud storage. */
        void saveBackupPresenter(boolean local);

        /** Initiates the backup-restore flow for local or cloud storage. */
        void restoreBackupPresenter(boolean local);

        /** Writes the cached backup to the file at the given URI. */
        void writeFileBackupLocal(Uri mUri);

        /** Reads and restores a backup from the file at the given URI. */
        void readFileBackupLocal(Uri mUri);

        /** Exports all notes via the share sheet. */
        void exportAllNotesPresenter();

        /** Imports parsed Google Keep data into the app database. */
        void importDataOtherApp(GoogleKeepImportResult result);

        /** Reads a Google Keep ZIP archive from the given URI. */
        void importFromZipOtherApp(Uri fileUri);

        /** Forwards the selected file URI to the view for processing. */
        void startProcessSelectedFileOtherApp(Uri fileUri);
    }
}
