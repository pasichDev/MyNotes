package com.pasich.mynotes.ui.view.activity;

import static com.pasich.mynotes.utils.FormattedDataUtil.lastDataCloudBackup;
import static com.pasich.mynotes.utils.constants.DriveScope.ACCESS_DRIVE_SCOPE;
import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.ARGUMENT_AUTO_BACKUP_CLOUD;
import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.FILE_NAME_BACKUP;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.services.drive.Drive;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.backup.JsonBackup;
import com.pasich.mynotes.databinding.ActivityBackupBinding;
import com.pasich.mynotes.databinding.FragmentBackupExportBinding;
import com.pasich.mynotes.ui.adapter.BackupPagerAdapter;
import com.pasich.mynotes.ui.contract.BackupContract;
import com.pasich.mynotes.ui.presenter.BackupPresenter;
import com.pasich.mynotes.ui.view.dialogs.ShareOptionsDialog;
import com.pasich.mynotes.ui.view.fragment.BackupExportFragment;
import com.pasich.mynotes.utils.backup.BackupCacheHelper;
import com.pasich.mynotes.utils.backup.CloudAuthHelper;
import com.pasich.mynotes.utils.backup.CloudCacheHelper;
import com.pasich.mynotes.utils.constants.CloudErrors;
import com.pasich.mynotes.utils.constants.DriveScope;
import com.pasich.mynotes.utils.constants.SnackBarInfo;

import java.util.Objects;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class BackupActivity extends BaseActivity implements BackupContract.view {

    @Inject
    public BackupContract.presenter presenter;
    /**
     * Restore local backup intent
     */
    private final ActivityResultLauncher<Intent> startIntentImport = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            if (result.getData() != null && result.getData().getData() != null) {
                presenter.readFileBackupLocal(result.getData().getData());
            }
        }
    });
    public ActivityBackupBinding binding;
    @Inject
    public BackupCacheHelper serviceCache;
    @Inject
    public GoogleSignInClient googleSignInClient;
    @Inject
    public CloudCacheHelper cloudCacheHelper;
    @Inject
    public CloudAuthHelper cloudAuthHelper;
    private BackupPagerAdapter pagerAdapter;
    private BackupExportFragment backupExportFragment;
    /**
     * Auth user cloud
     */
    private ActivityResultLauncher<Intent> startAuthIntent = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            cloudAuthHelper.getResultAuth(result.getData()).addOnFailureListener((GoogleSignInAccount) -> onInfoSnack(R.string.errorAuth, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG)).addOnSuccessListener((GoogleSignInAccount) -> {
                cloudCacheHelper.update(GoogleSignInAccount, GoogleSignIn.hasPermissions(GoogleSignInAccount, DriveScope.ACCESS_DRIVE_SCOPE), true);
                changeDataUserActivityFromAuth(true);
            });
        }
    });
    /**
     * Save local backup intent
     */
    private ActivityResultLauncher<Intent> startIntentExport = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            if (result.getData() != null) {
                presenter.writeFileBackupLocal(serviceCache, result.getData().getData());
            }
        }
    });
    private Dialog progressDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        selectTheme();
        binding = ActivityBackupBinding.inflate(getLayoutInflater());
        super.onCreate(savedInstanceState);
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
        pagerAdapter = new BackupPagerAdapter(this, presenter);
        binding.viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0:
                            tab.setText(R.string.backup_and_export);
                            break;
                        case 1:
                            tab.setText(R.string.import_data);
                            break;
                    }
                }).attach();

        // Встановлюємо слухач для отримання посилання на фрагмент
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (position == 0) {
                    // Отримуємо посилання на BackupExportFragment
                    backupExportFragment = (BackupExportFragment) getSupportFragmentManager()
                            .findFragmentByTag("f" + position);
                }
            }
        });
    }

    private FragmentBackupExportBinding getFragmentBinding() {
        if (backupExportFragment != null && backupExportFragment.getBinding() != null) {
            return backupExportFragment.getBinding();
        }
        // Fallback: спробуємо знайти фрагмент
        backupExportFragment = (BackupExportFragment) getSupportFragmentManager()
                .findFragmentByTag("f0");
        if (backupExportFragment != null) {
            return backupExportFragment.getBinding();
        }
        return null;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DriveScope.CONST_REQUEST_DRIVE_SCOPE) {
            cloudCacheHelper.setHasPermissionDrive(resultCode == RESULT_OK);
        }
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
        supportFinishAfterTransition();
        return true;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isDestroyed()) {
            presenter.detachView();
            startAuthIntent = null;
            startIntentExport = null;
        }
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public void editLastDataEditBackupCloud(long lastDate, boolean error) {
        FragmentBackupExportBinding fragmentBinding = getFragmentBinding();
        if (fragmentBinding == null) return;
        
        if (error) {
            showErrors(CloudErrors.LAST_BACKUP_EMPTY_DRIVE_VIEW);
        } else {
            fragmentBinding.lastBackupCloud.setText(getString(R.string.lastCloudCopy, lastDataCloudBackup(lastDate)));
        }
    }

    private void editSwitchSetAutoBackup(String text) {
        FragmentBackupExportBinding fragmentBinding = getFragmentBinding();
        if (fragmentBinding != null) {
            fragmentBinding.switchAutoCloud.setText(text);
        }
    }


    @Override
    public void initActivity() {
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        editSwitchSetAutoBackup(getResources().getStringArray(R.array.autoCloudVariants)[presenter.getDataManager().getSetCloudAuthBackup()]);
    }

    @Override
    public void initConnectAccount() {
        FragmentBackupExportBinding fragmentBinding = getFragmentBinding();
        if (fragmentBinding != null) {
            fragmentBinding.setIsPlayService(cloudCacheHelper.isInstallPlayMarket());
        }
        changeDataUserActivityFromAuth(cloudCacheHelper.isAuth());
    }

    /**
     * Start intent account login
     */
    @Override
    public void startIntentLogInUserCloud() {
        startAuthIntent.launch(googleSignInClient.getSignInIntent());
    }

    /**
     * Get Drive Object
     *
     * @return - drive object
     */
    public Drive getDrive() {
        return cloudAuthHelper.getDriveCredentialService(cloudCacheHelper.isAuth() ? cloudCacheHelper.getGoogleSignInAccount().getAccount() : null, this);
    }


    /**
     * Edit and update dataUser, from isAuth cloud
     *
     * @param isAuth - check auth cloud
     */
    private void changeDataUserActivityFromAuth(boolean isAuth) {
        FragmentBackupExportBinding fragmentBinding = getFragmentBinding();
        if (fragmentBinding == null) return;
        
        if (isAuth) {
            fragmentBinding.userNameDrive.setText(cloudCacheHelper.getGoogleSignInAccount().getEmail());
            fragmentBinding.setIsAuthUser(true);

            if (presenter.getDataManager().getLastBackupCloudId().equals("null")) {
                loadingLastBackupInfoCloud();
            } else
                editLastDataEditBackupCloud(presenter.getDataManager().getLastDataBackupCloud(), false);

        } else {
            fragmentBinding.lastBackupCloud.setText(getString(R.string.errorDriverAuthInfo));
            fragmentBinding.userNameDrive.setText(R.string.errorDriveAuth);
            fragmentBinding.setIsAuthUser(false);
        }
    }

    /**
     * Save local backup (2/3) - start intent save json file
     *
     * @param jsonValue - appData
     */
    @Override
    public void openIntentSaveBackup(JsonBackup jsonValue) {
        serviceCache.setJsonBackup(jsonValue);
        startIntentExport.launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, FILE_NAME_BACKUP));
    }

    /**
     * Restore local backup (2/3) - start intent load json file
     */
    @Override
    public void openIntentReadBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json");
        startIntentImport.launch(intent);
    }

    /**
     * Loading last backup information (id/date)
     */
    @Override
    public void loadingLastBackupInfoCloud() {
        final Drive mDriveCredential = getDrive();
        final int mError = checkErrorCloud(mDriveCredential);
        if (mError == CloudErrors.NO_ERROR) {
            FragmentBackupExportBinding fragmentBinding = getFragmentBinding();
            if (fragmentBinding != null) {
                fragmentBinding.lastBackupCloud.setText(R.string.checkLastBackupsCloud);
            }
            presenter.saveDataLoadingLastBackup(mDriveCredential);
        } else showErrors(mError);
    }

    /**
     * Write backup cloud (2/3)
     */
    @Override
    public void startWriteBackupCloud(JsonBackup jsonBackup) {
        final Drive mDriveCredential = getDrive();
        if (showErrors(checkErrorCloud(mDriveCredential))) {
            presenter.writeFileBackupCloud(mDriveCredential, jsonBackup);
        }
    }

    /**
     * Read backup cloud (2/3)
     */
    @Override
    public void startReadBackupCloud() {
        final Drive mDriveCredential = getDrive();
        final int mError = checkErrorCloud(mDriveCredential);
        if (!showErrors(checkErrorCloud(mDriveCredential))) {
            showErrors(mError);
        } else if (presenter.getDataManager().getLastBackupCloudId().equals("null")) {
            showErrors(CloudErrors.LAST_BACKUP_EMPTY_RESTORE);
        } else {
            presenter.readFileBackupCloud(mDriveCredential);
        }

    }

    /**
     * Visible progressBar write cloud backup
     */
    @Override
    public void visibleProgressBarCLoud() {
        FragmentBackupExportBinding fragmentBinding = getFragmentBinding();
        if (fragmentBinding != null) {
            fragmentBinding.setIsVisibleProgressCloud(true);
            fragmentBinding.progressBackupCloud.setProgress(10);
            fragmentBinding.percentProgress.setText(getString(R.string.percentProgress, 10));
        }
    }

    /**
     * Gone progressBar write cloud backup
     */
    @Override
    public void goneProgressBarCLoud() {
        FragmentBackupExportBinding fragmentBinding = getFragmentBinding();
        if (fragmentBinding != null) {
            fragmentBinding.progressBackupCloud.setVisibilityAfterHide(View.INVISIBLE);
            fragmentBinding.setIsVisibleProgressCloud(false);
            fragmentBinding.progressBackupCloud.setProgress(0);
            fragmentBinding.percentProgress.setText(getString(R.string.percentProgress, 0));
        }
    }

    @Override
    public void getClickedOffUpdate() {
        FragmentBackupExportBinding fragmentBinding = getFragmentBinding();
        if (fragmentBinding != null) {
            fragmentBinding.driveData.setClickable(false);
        }
    }

    /**
     * Listener progress uploader file
     *
     * @return - MediaHttpUploaderProgressListener
     */
    @Override
    public MediaHttpUploaderProgressListener getProcessListener() {
        return uploading -> {
            FragmentBackupExportBinding fragmentBinding = getFragmentBinding();
            if (fragmentBinding == null) return;
            
            switch (uploading.getUploadState()) {
                case INITIATION_STARTED -> {
                    fragmentBinding.progressBackupCloud.setProgress(20);
                    runOnUiThread(() -> fragmentBinding.percentProgress.setText(getString(R.string.percentProgress, 20)));
                }
                case INITIATION_COMPLETE -> {
                    fragmentBinding.progressBackupCloud.setProgress(50);
                    runOnUiThread(() -> fragmentBinding.percentProgress.setText(getString(R.string.percentProgress, 50)));
                }
                case MEDIA_IN_PROGRESS -> {
                    fragmentBinding.progressBackupCloud.setProgress(80);
                    runOnUiThread(() -> fragmentBinding.percentProgress.setText(getString(R.string.percentProgress, 80)));
                }
                case MEDIA_COMPLETE -> {
                    fragmentBinding.progressBackupCloud.setProgress(99);
                    runOnUiThread(() -> fragmentBinding.percentProgress.setText(getString(R.string.percentProgress, 99)));
                }
            }
        };
    }

    /**
     * Check error from request
     *
     * @param mDriveCredential - check isAuth user
     * @return - code error
     */
    private int checkErrorCloud(@Nullable Drive mDriveCredential) {
        if (!isNetworkConnected()) {
            return CloudErrors.NETWORK_ERROR;
        }
        if (!cloudCacheHelper.isHasPermissionDrive()) {
            return CloudErrors.PERMISSION_DRIVE;
        }

        if (!cloudCacheHelper.isAuth()) {
            return CloudErrors.ERROR_AUTH;
        }
        if (mDriveCredential == null) {
            return CloudErrors.CREDENTIAL;
        }

        return CloudErrors.NO_ERROR;
    }


    /**
     * Error processing
     *
     * @param errorCode - code error
     * @return - true - no errors
     */
    @Override
    public boolean showErrors(int errorCode) {
        Log.w("Drive","ShowErrors = " + errorCode);
        switch (errorCode) {
            case CloudErrors.CREDENTIAL:
                onInfoSnack(R.string.errorCredential, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
                break;
            case CloudErrors.PERMISSION_DRIVE:
                if (cloudCacheHelper.isAuth())
                    GoogleSignIn.requestPermissions(this, DriveScope.CONST_REQUEST_DRIVE_SCOPE, cloudCacheHelper.getGoogleSignInAccount(), ACCESS_DRIVE_SCOPE);
                else startIntentLogInUserCloud();
                break;
            case CloudErrors.NETWORK_ERROR:
                onInfoSnack(R.string.errorConnectedNetwork, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
                break;
            case CloudErrors.ERROR_AUTH:
                onInfoSnack(R.string.errorDriverAuthInfo, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
                break;
            case CloudErrors.LAST_BACKUP_EMPTY_DRIVE_VIEW:
                FragmentBackupExportBinding fragmentBinding = getFragmentBinding();
                if (fragmentBinding != null) {
                    fragmentBinding.lastBackupCloud.setText(getString(R.string.emptyBackups));
                }
                break;
            case CloudErrors.LAST_BACKUP_EMPTY_RESTORE:
                onInfoSnack(R.string.emptyBackups, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
                break;
            case CloudErrors.NETWORK_FALSE:
                onInfoSnack(R.string.errorDriveSync, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
                break;
            case CloudErrors.ERROR_CREATE_CLOUD_BACKUP:
                goneProgressBarCLoud();
                onInfoSnack(R.string.creteLocalCopyFail, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
                break;
            case CloudErrors.ERROR_RESTORE_BACKUP:
                onInfoSnack(R.string.restoreDataFall, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
                break;
            case CloudErrors.ERROR_LOAD_LAST_INFO_BACKUP:
                onInfoSnack(R.string.errorDriveSync, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
                FragmentBackupExportBinding fragmentBinding2 = getFragmentBinding();
                if (fragmentBinding2 != null) {
                    fragmentBinding2.lastBackupCloud.setText(R.string.errorLoadingLastBackupCloud);
                }
                break;
            default:
                return true;
        }
        return false;
    }

    @Override
    public void restoreFinish(int infoCode) {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        switch (infoCode) {
            case CloudErrors.OKAY_RESTORE -> {
                onInfoSnack(R.string.restoreDataOkay, null, SnackBarInfo.Success, Snackbar.LENGTH_LONG);
            }
            case CloudErrors.BACKUP_DESTROY -> {
                onInfoSnack(R.string.restoreDataFall, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
            }
            case CloudErrors.NETWORK_ERROR -> {
                goneProgressBarCLoud();
                onInfoSnack(R.string.errorDriveSync, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
            }
            default -> {
                Log.w("BACKUP_ACTIVITY", "Unknown restore result code: " + infoCode);
            }
        }
    }

    @Override
    public void dialogChoiceVariantAutoBackup() {
        if (showErrors(checkErrorCloud(getDrive()))) {
            new MaterialAlertDialogBuilder(this).setCancelable(true).setTitle(R.string.autoCloudBackupTitle).setSingleChoiceItems(getResources().getStringArray(R.array.autoCloudVariants), presenter.getDataManager().getSetCloudAuthBackup(), (dialog, item) -> {
                editSwitchSetAutoBackup(getResources().getStringArray(R.array.autoCloudVariants)[item]);
                presenter.getDataManager().getBackupCloudInfoPreference().setInt(ARGUMENT_AUTO_BACKUP_CLOUD, getResources().getIntArray(R.array.autoCloudIndexes)[item]);
                dialog.dismiss();

            }).create().show();
        }
    }


    @Override
    public void dialogRestoreData(boolean local) {
        final Drive mDriveCredential = getDrive();
        final MaterialAlertDialogBuilder restoreDialog = new MaterialAlertDialogBuilder(this).setCancelable(false).setTitle(R.string.restoreNotesTitle).setMessage(R.string.restoreNotesMessage).setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss()).setPositiveButton(local ? R.string.selectRestore : R.string.nextRestore, (dialog, which) -> {
            if (local) {
                openIntentReadBackup();
            } else {
                startReadBackupCloud();
            }
            dialog.dismiss();
        });


        if (local) {
            restoreDialog.create().show();
        } else {
            if (showErrors(checkErrorCloud(mDriveCredential))) {
                if (presenter.getDataManager().getLastBackupCloudId().equals("null")) {
                    showErrors(CloudErrors.LAST_BACKUP_EMPTY_RESTORE);
                } else {
                    restoreDialog.create().show();
                }
            }
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
        ShareOptionsDialog shareOptionsDialog =
            new ShareOptionsDialog(notes, isDataExport);
        shareOptionsDialog.show(getSupportFragmentManager(), "ShareOptionsDialog");
    }
}