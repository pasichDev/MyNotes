package com.pasich.mynotes.ui.view.activity;

import static com.pasich.mynotes.utils.constants.Backup.FILE_NAME_BACKUP_MNBKN;
import static com.pasich.mynotes.utils.file.FileExportUtils.GOOGLE_DRIVE_PACKAGE;
import static com.pasich.mynotes.utils.file.FileExportUtils.saveBackupToGoogleDrive;
import static com.pasich.mynotes.utils.navigation.ActivityResultKeys.EXTRA_UPDATE_THEME_STYLE;
import static com.pasich.mynotes.utils.navigation.ActivityResultKeys.RESULT_CODE_THEME_UPDATE;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayoutMediator;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.base.view.BackupOptionsCallback;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import com.pasich.mynotes.data.sync.GoogleDriveSyncBackend;
import com.pasich.mynotes.data.sync.RoomSyncStore;
import com.pasich.mynotes.data.sync.SyncService;
import com.pasich.mynotes.data.sync.SyncState;
import com.pasich.mynotes.databinding.ActivityBackupBinding;
import com.pasich.mynotes.ui.contract.BackupContract;
import com.pasich.mynotes.ui.presenter.BackupPresenter;
import com.pasich.mynotes.ui.view.dialogs.BackupOptionsDialog;
import com.pasich.mynotes.ui.view.dialogs.OtherAppImportDialog;
import com.pasich.mynotes.ui.view.dialogs.ShareOptionsDialog;
import com.pasich.mynotes.utils.adapters.BackupPagerAdapter;
import com.pasich.mynotes.utils.auth.FirebaseGoogleAuth;
import com.pasich.mynotes.utils.auth.GoogleCredentialAuth;
import com.pasich.mynotes.utils.auth.GoogleDriveAuthorization;
import com.pasich.mynotes.utils.backup.ScramblerBackupHelper;
import com.pasich.mynotes.utils.backup.local.BackupFileValidator;
import com.pasich.mynotes.utils.backup.models.JsonBackup;
import com.pasich.mynotes.utils.backup.models.googleKeep.GoogleKeepImportResult;
import com.pasich.mynotes.utils.constants.CloudErrors;
import com.pasich.mynotes.utils.constants.SnackBarInfo;
import com.pasich.mynotes.utils.file.DriveProcess;
import com.pasich.mynotes.utils.file.FileExportUtils;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/** Activity for creating and restoring app data backups. */
@AndroidEntryPoint
public class BackupActivity extends BaseActivity implements BackupContract.view {

    @Inject public BackupContract.presenter presenter;
    @Inject AppDatabase appDatabase;
    @Inject PreferenceHelper preferenceHelper;

    /** Save local backup intent */
    private final ActivityResultLauncher<Intent> intentExportDevice =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            if (result.getData() != null) {
                                Uri uri = result.getData().getData();
                                presenter.writeFileBackupLocal(uri);
                            }
                        }
                    });

    /** Restore local backup intent */
    private final ActivityResultLauncher<Intent> intentImportDevice =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            if (result.getData() != null && result.getData().getData() != null) {
                                Uri uri = result.getData().getData();

                                BackupFileValidator.isValidBackupFile(
                                        this,
                                        uri,
                                        new BackupFileValidator.BackupValidatorCallback() {
                                            @Override
                                            public void onValid(String fileName) {
                                                presenter.readFileBackupLocal(uri);
                                            }

                                            @Override
                                            public void onInvalid(String errorMessage) {
                                                onInfoSnack(
                                                        errorMessage,
                                                        null,
                                                        SnackBarInfo.Error,
                                                        Snackbar.LENGTH_LONG);
                                            }
                                        });
                            }
                        }
                    });

    public ActivityBackupBinding binding;
    ActivityResultLauncher<Intent> launcherImportDrive =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK
                                && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                presenter.readFileBackupLocal(result.getData().getData());
                            } else {
                                onInfoSnack(
                                        getString(R.string.file_not_selected),
                                        null,
                                        SnackBarInfo.Error,
                                        Snackbar.LENGTH_LONG);
                            }
                        } else {
                            onInfoSnack(
                                    getString(R.string.drive_selected_error),
                                    null,
                                    SnackBarInfo.Error,
                                    Snackbar.LENGTH_LONG);
                        }
                    });
    ActivityResultLauncher<Intent> launcherExportDrive =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            createLocalCopyFinish(true);
                        } else {
                            onInfoSnack(
                                    getString(R.string.export_drive_error),
                                    null,
                                    SnackBarInfo.Error,
                                    Snackbar.LENGTH_LONG);
                        }
                    });
    private boolean restoreSuccess = false;
    private OtherAppImportDialog importDialog;
    private Dialog progressDialog;
    private GoogleCredentialAuth googleCredentialAuth;
    private GoogleDriveAuthorization googleDriveAuthorization;
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
    @Inject FirebaseGoogleAuth firebaseGoogleAuth;

    @Override
    public void onRestoreSuccessFlag() {
        restoreSuccess = true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectTheme();
        binding = ActivityBackupBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        googleCredentialAuth =
                new GoogleCredentialAuth(this, getString(R.string.default_web_client_id));
        googleDriveAuthorization = new GoogleDriveAuthorization(this);
        updateGoogleSignInButton();
        binding.googleSignInButton.setOnClickListener(v -> onGoogleSignInClicked());
        binding.googleSignInButtonSignedOut.setOnClickListener(v -> onGoogleSignInClicked());
        binding.syncButton.setOnClickListener(v -> startSync());

        setupEdgeToEdgeInsets(binding.getRoot());
        presenter.attachView(this);
        presenter.viewIsReady();
        binding.setPresenter((BackupPresenter) presenter);

        setupTabs();

        getOnBackPressedDispatcher()
                .addCallback(
                        new OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                setEnabled(finishActivity());
                            }
                        });
    }

    private void updateGoogleSignInButton() {
        if (firebaseGoogleAuth == null || !firebaseGoogleAuth.isSignedIn()) {
            binding.googleProfile.setVisibility(View.GONE);
            binding.googleSignInButtonSignedOut.setVisibility(View.VISIBLE);
            return;
        }
        com.google.firebase.auth.FirebaseUser user = firebaseGoogleAuth.getCurrentUser();
        String name = user.getDisplayName();
        String email = user.getEmail();
        String label = name == null || name.trim().isEmpty() ? "Google" : name.trim();
        binding.googleName.setText(label);
        binding.googleEmail.setText(email == null ? "" : email);
        binding.googleAvatar.setText(label.substring(0, 1).toUpperCase(java.util.Locale.ROOT));
        binding.googleProfile.setVisibility(View.VISIBLE);
        binding.googleSignInButtonSignedOut.setVisibility(View.GONE);
    }

    private void startSync() {
        if (!firebaseGoogleAuth.isSignedIn()) {
            onInfoSnack(
                    R.string.google_sign_in_failed, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
            return;
        }
        binding.syncButton.setEnabled(false);
        binding.syncButton.setVisibility(View.GONE);
        binding.syncProgress.setVisibility(View.VISIBLE);
        googleDriveAuthorization.authorize(
                this,
                new GoogleDriveAuthorization.Callback() {
                    @Override
                    public void onAuthorized(@androidx.annotation.NonNull String accessToken) {
                        syncExecutor.execute(
                                () -> {
                                    SyncState state =
                                            new SyncService(
                                                            new RoomSyncStore(
                                                                    BackupActivity.this,
                                                                    appDatabase,
                                                                    preferenceHelper))
                                                    .sync(new GoogleDriveSyncBackend(accessToken));
                                    runOnUiThread(() -> finishSync(state));
                                });
                    }

                    @Override
                    public void onError(@androidx.annotation.NonNull Exception error) {
                        runOnUiThread(() -> finishSyncError(error));
                    }
                });
    }

    private void finishSync(SyncState state) {
        binding.syncButton.setEnabled(true);
        binding.syncButton.setVisibility(View.VISIBLE);
        binding.syncProgress.setVisibility(View.GONE);
        if (state.getStatus() == SyncState.Status.SUCCESS) {
            scheduleAutomaticSync();
            int conflicts = state.getConflictCount();
            onInfoSnack(
                    conflicts == 0
                            ? getString(R.string.sync_success)
                            : getString(R.string.sync_success_conflicts, conflicts),
                    null,
                    SnackBarInfo.Success,
                    Snackbar.LENGTH_LONG);
        } else {
            finishSyncError(new IllegalStateException(state.getErrorMessage()));
        }
    }

    private void scheduleAutomaticSync() {
        Constraints constraints =
                new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                                com.pasich.mynotes.data.sync.GoogleDriveSyncWorker.class,
                                6,
                                TimeUnit.HOURS)
                        .setConstraints(constraints)
                        .build();
        WorkManager.getInstance(getApplicationContext())
                .enqueueUniquePeriodicWork(
                        "mynotes-drive-sync", ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    private void finishSyncError(Exception error) {
        binding.syncButton.setEnabled(true);
        binding.syncButton.setVisibility(View.VISIBLE);
        binding.syncProgress.setVisibility(View.GONE);
        onInfoSnack(
                getString(
                        R.string.sync_failed,
                        error.getMessage() == null
                                ? error.getClass().getSimpleName()
                                : error.getMessage()),
                null,
                SnackBarInfo.Error,
                Snackbar.LENGTH_LONG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (googleDriveAuthorization != null
                && googleDriveAuthorization.onActivityResult(requestCode, resultCode, data)) return;
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void onGoogleSignInClicked() {
        if (firebaseGoogleAuth.isSignedIn()) {
            firebaseGoogleAuth.signOut();
            updateGoogleSignInButton();
            googleCredentialAuth.signOut(
                    new GoogleCredentialAuth.SignOutCallback() {
                        @Override
                        public void onSuccess() {
                            // Firebase state was already reflected immediately.
                        }

                        @Override
                        public void onError(@androidx.annotation.NonNull Exception error) {
                            // Credential Manager cleanup is best-effort.
                        }
                    });
            return;
        }
        googleCredentialAuth.signIn(
                this,
                new GoogleCredentialAuth.Callback() {
                    @Override
                    public void onSuccess(
                            @androidx.annotation.NonNull
                                    com.pasich.mynotes.utils.auth.GoogleCredential credential) {
                        firebaseGoogleAuth.signIn(
                                credential,
                                new FirebaseGoogleAuth.Callback() {
                                    @Override
                                    public void onSuccess(
                                            @androidx.annotation.NonNull
                                                    com.google.firebase.auth.FirebaseUser user) {
                                        updateGoogleSignInButton();
                                        scheduleAutomaticSync();
                                    }

                                    @Override
                                    public void onError(
                                            @androidx.annotation.NonNull Exception error) {
                                        onInfoSnack(
                                                R.string.google_sign_in_failed,
                                                null,
                                                SnackBarInfo.Error,
                                                Snackbar.LENGTH_LONG);
                                    }
                                });
                    }

                    @Override
                    public void onError(@androidx.annotation.NonNull Exception error) {
                        onInfoSnack(
                                R.string.google_sign_in_failed,
                                null,
                                SnackBarInfo.Error,
                                Snackbar.LENGTH_LONG);
                    }
                });
    }

    private void setupTabs() {
        BackupPagerAdapter pagerAdapter = new BackupPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(
                        binding.tabLayout,
                        binding.viewPager,
                        (tab, position) -> {
                            switch (position) {
                                case 0:
                                    tab.setText(R.string.backup_and_export);
                                    break;
                                case 1:
                                    tab.setText(R.string.import_data);
                                    break;
                            }
                        })
                .attach();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finishActivity();
        }
        return true;
    }

    private boolean finishActivity() {
        Intent resultIntent = new Intent();

        if (restoreSuccess) {
            resultIntent.putExtra(EXTRA_UPDATE_THEME_STYLE, true);
            setResult(RESULT_CODE_THEME_UPDATE, resultIntent);
        } else {
            setResult(0);
        }

        supportFinishAfterTransition();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        syncExecutor.shutdownNow();
        if (isDestroyed()) {
            presenter.detachView();
        }
    }

    @Override
    public void initActivity() {
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    }

    /** Save local backup (2/3) - start intent save json file */
    @Override
    public void openIntentSaveBackup(JsonBackup jsonBackup) {
        BackupOptionsDialog.newInstance(
                        new BackupOptionsCallback() {
                            @Override
                            public void onGoogleDrive() {
                                saveBackupToGoogleDrive(
                                        BackupActivity.this,
                                        ScramblerBackupHelper.encodeString(jsonBackup),
                                        new DriveProcess() {
                                            @Override
                                            public void onSuccess(Uri uri, String nameFile) {
                                                // Формуємо інтент для Drive
                                                Intent intent = new Intent(Intent.ACTION_SEND);
                                                intent.setType("*/*");
                                                intent.putExtra(
                                                        Intent.EXTRA_TITLE, FILE_NAME_BACKUP_MNBKN);
                                                intent.putExtra(
                                                        Intent.EXTRA_MIME_TYPES,
                                                        new String[] {
                                                            "application/zip",
                                                            "application/octet-stream"
                                                        });
                                                intent.putExtra(Intent.EXTRA_STREAM, uri);
                                                intent.putExtra(
                                                        Intent.EXTRA_SUBJECT,
                                                        FILE_NAME_BACKUP_MNBKN);
                                                intent.setPackage(GOOGLE_DRIVE_PACKAGE);
                                                intent.addFlags(
                                                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                                launchGoogleDriveIntent(
                                                        launcherExportDrive,
                                                        intent,
                                                        R.string.export_drive_error);
                                            }

                                            @Override
                                            public void onError(String error) {
                                                onInfoSnack(
                                                        error,
                                                        null,
                                                        SnackBarInfo.Error,
                                                        Snackbar.LENGTH_LONG);
                                            }
                                        });
                            }

                            @Override
                            public void onDeviceStorage() {
                                intentExportDevice.launch(
                                        new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                                .addCategory(Intent.CATEGORY_OPENABLE)
                                                .putExtra(
                                                        Intent.EXTRA_TITLE, FILE_NAME_BACKUP_MNBKN)
                                                .setType("*/*")
                                                .putExtra(
                                                        Intent.EXTRA_MIME_TYPES,
                                                        new String[] {
                                                            "application/zip",
                                                            "application/octet-stream"
                                                        }));
                            }
                        },
                        false)
                .show(getSupportFragmentManager(), "ShareOptionsDialog");
    }

    /** Restore local backup (2/3) - start intent load json file */
    @Override
    public void openIntentReadBackup() {
        BackupOptionsDialog.newInstance(
                        new BackupOptionsCallback() {
                            @Override
                            public void onGoogleDrive() {
                                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                                intent.putExtra(Intent.EXTRA_TITLE, FILE_NAME_BACKUP_MNBKN);
                                intent.setType("*/*");
                                intent.putExtra(
                                        Intent.EXTRA_MIME_TYPES,
                                        new String[] {
                                            "application/json",
                                            "application/zip",
                                            "application/octet-stream"
                                        });
                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                intent.setPackage(GOOGLE_DRIVE_PACKAGE);
                                launchGoogleDriveIntent(
                                        launcherImportDrive, intent, R.string.drive_selected_error);
                            }

                            @Override
                            public void onDeviceStorage() {
                                intentImportDevice.launch(
                                        new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                                .addCategory(Intent.CATEGORY_OPENABLE)
                                                .setType("*/*")
                                                .putExtra(
                                                        Intent.EXTRA_TITLE, FILE_NAME_BACKUP_MNBKN)
                                                .putExtra(
                                                        Intent.EXTRA_MIME_TYPES,
                                                        new String[] {
                                                            "application/json",
                                                            "application/zip",
                                                            "application/octet-stream"
                                                        }));
                            }
                        },
                        true)
                .show(getSupportFragmentManager(), "ShareOptionsDialog");
    }

    @Override
    public void showErrorsText(int errorCode, int string) {
        onInfoSnack(string, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
    }

    private void launchGoogleDriveIntent(
            ActivityResultLauncher<Intent> launcher, Intent intent, int errorMessage) {
        if (!FileExportUtils.canHandleIntent(getPackageManager(), intent)) {
            onInfoSnack(errorMessage, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
            return;
        }

        try {
            launcher.launch(intent);
        } catch (ActivityNotFoundException error) {
            Log.w("BACKUP_ACTIVITY", "Google Drive cannot handle backup intent", error);
            onInfoSnack(errorMessage, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
        }
    }

    @Override
    public void restoreFinish(int infoCode) {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        switch (infoCode) {
            case CloudErrors.OKAY_RESTORE ->
                    onInfoSnack(
                            R.string.restoreDataOkay,
                            null,
                            SnackBarInfo.Success,
                            Snackbar.LENGTH_LONG);
            case CloudErrors.BACKUP_DESTROY ->
                    onInfoSnack(
                            R.string.restoreDataFall,
                            null,
                            SnackBarInfo.Error,
                            Snackbar.LENGTH_LONG);
            case CloudErrors.NETWORK_ERROR ->
                    onInfoSnack(
                            R.string.errorDriveSync,
                            null,
                            SnackBarInfo.Error,
                            Snackbar.LENGTH_LONG);
            default -> Log.w("BACKUP_ACTIVITY", "Unknown restore result code: " + infoCode);
        }
    }

    @Override
    public void dialogRestoreData(boolean local) {
        final MaterialAlertDialogBuilder restoreDialog =
                new MaterialAlertDialogBuilder(this)
                        .setCancelable(false)
                        .setTitle(R.string.restoreNotesTitle)
                        .setMessage(R.string.restoreNotesMessage)
                        .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                        .setPositiveButton(
                                local ? R.string.selectRestore : R.string.nextRestore,
                                (dialog, which) -> {
                                    if (local) {
                                        openIntentReadBackup();
                                    }
                                    dialog.dismiss();
                                });

        if (local) {
            restoreDialog.create().show();
        }
    }

    @Override
    public void showProcessRestoreDialog() {
        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(this, R.style.progressDialogRestore)
                        .setCancelable(false)
                        .setView(R.layout.view_restore_data);
        progressDialog = builder.create();
        progressDialog.show();
    }

    @Override
    public void emptyDataToBackup() {
        onInfoSnack(R.string.emptyDataToBackup, null, SnackBarInfo.Info, Snackbar.LENGTH_LONG);
    }

    @Override
    public void createLocalCopyFinish(boolean error) {
        if (error) {
            onInfoSnack(
                    R.string.creteLocalCopyOkay, null, SnackBarInfo.Success, Snackbar.LENGTH_LONG);
        } else {
            onInfoSnack(
                    R.string.creteLocalCopyFail, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
        }
    }

    @Override
    public void initListeners() {}

    @Override
    public void openShareOptionsDialog(java.util.List<Note> notes, boolean isDataExport) {
        ShareOptionsDialog shareOptionsDialog = new ShareOptionsDialog(notes, isDataExport);
        shareOptionsDialog.show(getSupportFragmentManager(), "ShareOptionsDialog");
    }

    @Override
    public void processSelectedFileOtherApp(Uri fileUri) {
        importDialog = OtherAppImportDialog.newInstance();
        importDialog.show(getSupportFragmentManager(), "import_progress");
        importDialog.updateProgress(true);
        presenter.importFromZipOtherApp(fileUri);
    }

    @Override
    public void showImportResultOtherApp(GoogleKeepImportResult result) {
        importDialog.showResult(result);
    }

    @Override
    public void setupImportCallbackOtherApp(GoogleKeepImportResult result) {
        importDialog.setCallback(importResult -> presenter.importDataOtherApp(importResult));
    }
}
