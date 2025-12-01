package com.pasich.mynotes.ui.view.activity;

import static com.pasich.mynotes.utils.constants.Backup.FILE_NAME_BACKUP_MNBKN;
import static com.pasich.mynotes.utils.file.FileExportUtils.GOOGLE_DRIVE_PACKAGE;
import static com.pasich.mynotes.utils.file.FileExportUtils.saveBackupToGoogleDrive;
import static com.pasich.mynotes.utils.navigation.ActivityResultKeys.EXTRA_UPDATE_THEME_STYLE;
import static com.pasich.mynotes.utils.navigation.ActivityResultKeys.RESULT_CODE_THEME_UPDATE;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayoutMediator;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.base.view.BackupOptionsCallback;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ActivityBackupBinding;
import com.pasich.mynotes.ui.contract.BackupContract;
import com.pasich.mynotes.ui.presenter.BackupPresenter;
import com.pasich.mynotes.ui.view.dialogs.BackupOptionsDialog;
import com.pasich.mynotes.ui.view.dialogs.OtherAppImportDialog;
import com.pasich.mynotes.ui.view.dialogs.ShareOptionsDialog;
import com.pasich.mynotes.utils.adapters.BackupPagerAdapter;
import com.pasich.mynotes.utils.backup.ScramblerBackupHelper;
import com.pasich.mynotes.utils.backup.local.BackupFileValidator;
import com.pasich.mynotes.utils.backup.models.JsonBackup;
import com.pasich.mynotes.utils.backup.models.googleKeep.GoogleKeepImportResult;
import com.pasich.mynotes.utils.constants.CloudErrors;
import com.pasich.mynotes.utils.constants.SnackBarInfo;
import com.pasich.mynotes.utils.file.DriveProcess;

import java.util.Objects;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class BackupActivity extends BaseActivity implements BackupContract.view {

    @Inject
    public BackupContract.presenter presenter;
    /**
     * Save local backup intent
     */
    private final ActivityResultLauncher<Intent> intentExportDevice = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            if (result.getData() != null) {
                Uri uri = result.getData().getData();
                presenter.writeFileBackupLocal(uri);

            }
        }
    });
    /**
     * Restore local backup intent
     */
    private final ActivityResultLauncher<Intent> intentImportDevice = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            if (result.getData() != null && result.getData().getData() != null) {
                Uri uri = result.getData().getData();

                BackupFileValidator.isValidBackupFile(this, uri, new BackupFileValidator.BackupValidatorCallback() {
                    @Override
                    public void onValid(String fileName) {
                        presenter.readFileBackupLocal(uri);
                    }

                    @Override
                    public void onInvalid(String errorMessage) {
                        onInfoSnack(errorMessage, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
                    }
                });

            }
        }
    });
    public ActivityBackupBinding binding;
    ActivityResultLauncher<Intent> launcherImportDrive = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        presenter.readFileBackupLocal(result.getData().getData());
                    } else {
                        onInfoSnack(getString(R.string.file_not_selected), null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
                    }
                } else {
                    onInfoSnack(getString(R.string.drive_selected_error), null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
                }
            }
    );
    ActivityResultLauncher<Intent> launcherExportDrive = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    createLocalCopyFinish(true);
                } else {
                    onInfoSnack(getString(R.string.export_drive_error), null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
                }
            }
    );
    private boolean restoreSuccess = false;
    private OtherAppImportDialog importDialog;
    private Dialog progressDialog;

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

        setupEdgeToEdgeInsets(binding.getRoot());
        presenter.attachView(this);
        presenter.viewIsReady();
        binding.setPresenter((BackupPresenter) presenter);

        setupTabs();

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(finishActivity());
            }
        });
    }

    private void setupTabs() {
        BackupPagerAdapter pagerAdapter = new BackupPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.backup_and_export);
                    break;
                case 1:
                    tab.setText(R.string.import_data);
                    break;
            }
        }).attach();

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
        if (isDestroyed()) {
            presenter.detachView();
        }
    }


    @Override
    public void initActivity() {
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    }

    /**
     * Save local backup (2/3) - start intent save json file
     */
    @Override
    public void openIntentSaveBackup(JsonBackup jsonBackup) {
        BackupOptionsDialog.newInstance(new BackupOptionsCallback() {
            @Override
            public void onGoogleDrive() {
                saveBackupToGoogleDrive(BackupActivity.this,
                        ScramblerBackupHelper.encodeString(jsonBackup),
                        new DriveProcess() {
                            @Override
                            public void onSuccess(Uri uri, String nameFile) {
                                // Формуємо інтент для Drive
                                Intent intent = new Intent(Intent.ACTION_SEND);
                                intent.setType("*/*");
                                intent.putExtra(Intent.EXTRA_TITLE, FILE_NAME_BACKUP_MNBKN);
                                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                                        "application/zip",
                                        "application/octet-stream"
                                });
                                intent.putExtra(Intent.EXTRA_STREAM, uri);
                                intent.putExtra(Intent.EXTRA_SUBJECT, FILE_NAME_BACKUP_MNBKN);
                                intent.setPackage(GOOGLE_DRIVE_PACKAGE);
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                launcherExportDrive.launch(intent);
                            }

                            @Override
                            public void onError(String error) {
                                onInfoSnack(error, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
                            }
                        });
            }

            @Override
            public void onDeviceStorage() {
                intentExportDevice.launch(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(Intent.EXTRA_TITLE, FILE_NAME_BACKUP_MNBKN)
                        .setType("*/*")
                        .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                                "application/zip",
                                "application/octet-stream"
                        }));
            }

        }, false).show(getSupportFragmentManager(), "ShareOptionsDialog");


    }

    /**
     * Restore local backup (2/3) - start intent load json file
     */
    @Override
    public void openIntentReadBackup() {
        BackupOptionsDialog.newInstance(new BackupOptionsCallback() {
            @Override
            public void onGoogleDrive() {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.putExtra(Intent.EXTRA_TITLE, FILE_NAME_BACKUP_MNBKN);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/json",
                        "application/zip",
                        "application/octet-stream"
                });
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setPackage(GOOGLE_DRIVE_PACKAGE);
                launcherImportDrive.launch(intent);
            }

            @Override
            public void onDeviceStorage() {
                intentImportDevice.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                        .putExtra(Intent.EXTRA_TITLE, FILE_NAME_BACKUP_MNBKN)
                        .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                                "application/json",
                                "application/zip",
                                "application/octet-stream"
                        }));
            }

        }, true).show(getSupportFragmentManager(), "ShareOptionsDialog");


    }

    @Override
    public void showErrorsText(int errorCode, int string) {
        onInfoSnack(string, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
    }

    @Override
    public void restoreFinish(int infoCode) {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        switch (infoCode) {
            case CloudErrors.OKAY_RESTORE ->
                    onInfoSnack(R.string.restoreDataOkay, null, SnackBarInfo.Success, Snackbar.LENGTH_LONG);
            case CloudErrors.BACKUP_DESTROY ->
                    onInfoSnack(R.string.restoreDataFall, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
            case CloudErrors.NETWORK_ERROR ->
                    onInfoSnack(R.string.errorDriveSync, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
            default -> Log.w("BACKUP_ACTIVITY", "Unknown restore result code: " + infoCode);
        }
    }


    @Override
    public void dialogRestoreData(boolean local) {
        final MaterialAlertDialogBuilder restoreDialog = new MaterialAlertDialogBuilder(this).setCancelable(false).setTitle(R.string.restoreNotesTitle).setMessage(R.string.restoreNotesMessage).setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss()).setPositiveButton(local ? R.string.selectRestore : R.string.nextRestore, (dialog, which) -> {
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
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.progressDialogRestore).setCancelable(false).setView(R.layout.view_restore_data);
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
            onInfoSnack(R.string.creteLocalCopyOkay, null, SnackBarInfo.Success, Snackbar.LENGTH_LONG);
        } else {
            onInfoSnack(R.string.creteLocalCopyFail, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
        }
    }

    @Override
    public void initListeners() {
    }

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