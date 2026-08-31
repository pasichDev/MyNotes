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
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.base.view.BackupOptionsCallback;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.entities.SyncConflictEntity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import com.pasich.mynotes.data.sync.RoomSyncStore;
import com.pasich.mynotes.data.sync.SyncBundleCodec;
import com.pasich.mynotes.data.sync.SyncResolution;
import com.pasich.mynotes.data.sync.SyncSnapshot;
import com.pasich.mynotes.data.sync.SyncState;
import com.pasich.mynotes.databinding.ActivityBackupBinding;
import com.pasich.mynotes.ui.contract.BackupContract;
import com.pasich.mynotes.ui.presenter.BackupPresenter;
import com.pasich.mynotes.ui.sync.SyncCoordinator;
import com.pasich.mynotes.ui.sync.SyncCoordinatorFactory;
import com.pasich.mynotes.ui.view.dialogs.BackupOptionsDialog;
import com.pasich.mynotes.ui.view.dialogs.OtherAppImportDialog;
import com.pasich.mynotes.ui.view.dialogs.ShareOptionsDialog;
import com.pasich.mynotes.ui.view.fragment.mydata.AccountSyncFragment;
import com.pasich.mynotes.utils.adapters.BackupPagerAdapter;
import com.pasich.mynotes.utils.auth.FirebaseGoogleAuth;
import com.pasich.mynotes.utils.backup.ScramblerBackupHelper;
import com.pasich.mynotes.utils.backup.local.BackupFileValidator;
import com.pasich.mynotes.utils.backup.models.JsonBackup;
import com.pasich.mynotes.utils.backup.models.googleKeep.GoogleKeepImportResult;
import com.pasich.mynotes.utils.constants.CloudErrors;
import com.pasich.mynotes.utils.constants.SnackBarInfo;
import com.pasich.mynotes.utils.file.DriveProcess;
import com.pasich.mynotes.utils.file.FileExportUtils;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/** Activity for creating and restoring app data backups. */
@AndroidEntryPoint
public class BackupActivity extends BaseActivity
        implements BackupContract.view, AccountSyncFragment.Host {

    private static final String TAG = "BackupActivity";
    private static final String BACKGROUND_SYNC_WORK_NAME = "mynotes-drive-sync";

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
    private RoomSyncStore roomSyncStore;
    private SyncCoordinatorFactory.Result syncSetup;
    @Nullable private AccountSyncFragment accountTab;
    private boolean syncRunning;
    private SyncCoordinator syncCoordinator;
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
    @Inject FirebaseGoogleAuth firebaseGoogleAuth;
    private boolean updatingSyncControls;

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

        syncSetup =
                SyncCoordinatorFactory.create(
                        this, appDatabase, preferenceHelper, firebaseGoogleAuth, syncExecutor);
        if (syncSetup == null) {
            // No Firebase configuration in this build; GoogleCredentialAuth rejects a blank client
            // ID, so the sync controls are hidden instead of crashing the screen.
            if (accountTab != null) {
                accountTab.showUnavailable();
            }
        }
        if (syncSetup != null) {
            syncCoordinator = syncSetup.getCoordinator();
            roomSyncStore = syncSetup.getStore();
            updateGoogleSignInButton();
        }

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

    /** The account tab draws itself from the state the next updateSyncUi() pushes. */
    private void updateGoogleSignInButton() {
        updateSyncUi();
    }

    /**
     * Loads the sync state off the main thread and then draws it.
     *
     * <p>The state and the conflict list live in Room, which refuses main-thread access; the
     * profile comes from Firebase and is safe to read here.
     */
    /** True while the screen can still be drawn into. */
    private boolean canSchedule() {
        return syncCoordinator != null && !isFinishing() && !isDestroyed();
    }

    /**
     * Runs background work without letting a finished screen crash the app.
     *
     * <p>Checking {@code isShutdown()} first leaves a race between the check and the submission, so
     * the rejection is caught instead.
     */
    private void runInBackground(Runnable task) {
        try {
            syncExecutor.execute(
                    () -> {
                        try {
                            task.run();
                        } catch (RuntimeException error) {
                            // An exception escaping a worker thread kills the process; a failed
                            // background read must degrade the screen, not the app.
                            Log.e(TAG, "Background work failed", error);
                        }
                    });
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            Log.w(TAG, "Background work could not be scheduled; the screen is gone", rejected);
        }
    }

    private void updateSyncUi() {
        if (!canSchedule()) return;
        SyncCoordinator.Profile profile = syncCoordinator.getProfile();
        runInBackground(
                () -> {
                    SyncState state = syncCoordinator.getLastState();
                    int unresolved = unresolvedConflictCount(syncCoordinator.getConflicts());
                    runOnUiThread(
                            () -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    renderSyncUi(profile, state, unresolved);
                                }
                            });
                });
    }

    private void renderSyncUi(
            SyncCoordinator.Profile profile, SyncState state, int unresolvedConflicts) {
        if (accountTab == null || !accountTab.isBound()) {
            return;
        }
        accountTab.render(
                profile,
                state,
                profile.isSignedIn() ? unresolvedConflicts : 0,
                profile.isSignedIn() && syncCoordinator.isBackgroundSyncEnabled(),
                resolveStatusText(profile.isSignedIn(), state, unresolvedConflicts),
                formatLastSync(state));
        accountTab.setSyncing(syncRunning);
    }

    private void startSync() {
        if (!syncCoordinator.getProfile().isSignedIn()) {
            onInfoSnack(
                    R.string.google_sign_in_failed, null, SnackBarInfo.Error, Snackbar.LENGTH_LONG);
            return;
        }
        runInBackground(
                () -> {
                    boolean neverSynced =
                            syncCoordinator.getLastState().getLastSuccessfulSyncAt() == null;
                    runOnUiThread(
                            () -> {
                                if (isFinishing() || isDestroyed()) {
                                    return;
                                }
                                if (neverSynced) {
                                    prepareFirstSyncConfirmation();
                                } else {
                                    runSync();
                                }
                            });
                });
    }

    private void prepareFirstSyncConfirmation() {
        syncRunning = true;
        if (accountTab != null) accountTab.setSyncing(true);
        runInBackground(
                () -> {
                    try {
                        SyncSnapshot snapshot = roomSyncStore.readSnapshot();
                        byte[] bundle =
                                new SyncBundleCodec().encode(snapshot, java.time.Instant.now());
                        long attachmentBytes = 0L;
                        Set<String> seenAttachmentHashes = new HashSet<>();
                        for (com.pasich.mynotes.data.sync.SyncRecord record :
                                snapshot.getLiveRecords(
                                        com.pasich.mynotes.data.sync.SyncRecord.Type.NOTE)) {
                            JsonElement manifest = record.getPayload().get("attachmentsManifest");
                            if (manifest != null && manifest.isJsonArray()) {
                                for (JsonElement value : manifest.getAsJsonArray()) {
                                    if (value.isJsonObject()
                                            && value.getAsJsonObject().has("size")) {
                                        String hash =
                                                value.getAsJsonObject().has("sha256")
                                                        ? value.getAsJsonObject()
                                                                .get("sha256")
                                                                .getAsString()
                                                        : null;
                                        if (hash == null || seenAttachmentHashes.add(hash)) {
                                            attachmentBytes +=
                                                    Math.max(
                                                            0L,
                                                            value.getAsJsonObject()
                                                                    .get("size")
                                                                    .getAsLong());
                                        }
                                    }
                                }
                            }
                        }
                        long estimatedBytes = bundle.length + attachmentBytes;
                        runOnUiThread(
                                () -> {
                                    syncRunning = false;
                                    if (accountTab != null) accountTab.setSyncing(false);
                                    new MaterialAlertDialogBuilder(this)
                                            .setTitle(R.string.sync_first_sync_title)
                                            .setMessage(
                                                    getString(
                                                            R.string.sync_first_sync_message,
                                                            snapshot.getRecords().size(),
                                                            formatBytes(estimatedBytes)))
                                            .setNegativeButton(
                                                    R.string.sync_first_sync_cancel,
                                                    (dialog, which) -> updateSyncUi())
                                            .setPositiveButton(
                                                    R.string.sync_now, (dialog, which) -> runSync())
                                            .show();
                                });
                    } catch (Exception error) {
                        runOnUiThread(
                                () -> {
                                    syncRunning = false;
                                    if (accountTab != null) accountTab.setSyncing(false);
                                    finishSyncError(error);
                                });
                    }
                });
    }

    private void runSync() {
        syncRunning = true;
        if (accountTab != null) accountTab.setSyncing(true);
        syncCoordinator.syncNow(
                this,
                new SyncCoordinator.Callback<SyncState>() {
                    @Override
                    public void onSuccess(@NonNull SyncState state) {
                        finishSync(state);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        finishSyncError(error);
                    }
                });
    }

    @NonNull
    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0d);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0d * 1024.0d));
    }

    private void onGoogleSignInClicked() {
        if (syncCoordinator.getProfile().isSignedIn()) {
            syncCoordinator.disconnect(
                    new SyncCoordinator.Callback<SyncCoordinator.Profile>() {
                        @Override
                        public void onSuccess(@NonNull SyncCoordinator.Profile value) {
                            updateGoogleSignInButton();
                        }

                        @Override
                        public void onError(@NonNull Exception error) {
                            updateGoogleSignInButton();
                        }
                    });
            return;
        }
        syncCoordinator.connect(
                this,
                new SyncCoordinator.Callback<SyncCoordinator.Profile>() {
                    @Override
                    public void onSuccess(@NonNull SyncCoordinator.Profile value) {
                        updateGoogleSignInButton();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        onInfoSnack(
                                R.string.google_sign_in_failed,
                                null,
                                SnackBarInfo.Error,
                                Snackbar.LENGTH_LONG);
                    }
                });
    }

    private void finishSync(SyncState state) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        syncRunning = false;
        if (accountTab != null) accountTab.setSyncing(false);
        if (state.getStatus() == SyncState.Status.SUCCESS) {
            int conflicts = state.getConflictCount();
            updateSyncUi();
            onInfoSnack(
                    conflicts == 0
                            ? getString(R.string.sync_success)
                            : getString(R.string.sync_success_conflicts, conflicts),
                    null,
                    SnackBarInfo.Success,
                    Snackbar.LENGTH_LONG);
            if (conflicts > 0) {
                showNextConflictDialog();
            }
        } else {
            finishSyncError(new IllegalStateException(state.getErrorMessage()));
        }
    }

    private void enableBackgroundSyncWork() {
        Constraints constraints =
                new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                                com.pasich.mynotes.data.sync.GoogleDriveSyncWorker.class,
                                6,
                                TimeUnit.HOURS)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                        .build();
        WorkManager.getInstance(getApplicationContext())
                .enqueueUniquePeriodicWork(
                        BACKGROUND_SYNC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    private void disableBackgroundSyncWork() {
        WorkManager.getInstance(getApplicationContext())
                .cancelUniqueWork(BACKGROUND_SYNC_WORK_NAME);
    }

    private void finishSyncError(Exception error) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        syncRunning = false;
        if (accountTab != null) accountTab.setSyncing(false);
        updateSyncUi();
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
        if (syncCoordinator != null
                && syncCoordinator.onActivityResult(requestCode, resultCode, data)) return;
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void onBackgroundSyncToggled(boolean enabled) {
        if (updatingSyncControls) return;
        if (!syncCoordinator.getProfile().isSignedIn()) {
            updateSyncUi();
            onInfoSnack(
                    R.string.sync_sign_in_required, null, SnackBarInfo.Info, Snackbar.LENGTH_LONG);
            return;
        }
        syncCoordinator.setBackgroundSyncEnabled(enabled);
        updateSyncUi();
        onInfoSnack(
                enabled ? R.string.sync_background_enabled : R.string.sync_background_disabled,
                null,
                SnackBarInfo.Info,
                Snackbar.LENGTH_LONG);
    }

    private void showNextConflictDialog() {
        runInBackground(
                () -> {
                    List<SyncConflictEntity> unresolved =
                            unresolvedConflicts(syncCoordinator.getConflicts());
                    runOnUiThread(
                            () -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    showConflictDialog(unresolved);
                                }
                            });
                });
    }

    private void showConflictDialog(List<SyncConflictEntity> unresolved) {
        if (unresolved.isEmpty()) {
            updateSyncUi();
            return;
        }
        SyncConflictEntity conflict = unresolved.get(0);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.sync_conflict_title, unresolved.size()))
                .setMessage(buildConflictMessage(conflict))
                .setNegativeButton(R.string.sync_conflict_later, null)
                .setNeutralButton(
                        R.string.sync_conflict_keep_local,
                        (dialog, which) -> resolveConflict(conflict.id, SyncResolution.KEEP_LOCAL))
                .setPositiveButton(
                        R.string.sync_conflict_keep_drive,
                        (dialog, which) -> resolveConflict(conflict.id, SyncResolution.KEEP_DRIVE))
                .show();
    }

    private void resolveConflict(long conflictId, SyncResolution resolution) {
        syncCoordinator.resolveConflict(
                conflictId,
                resolution,
                new SyncCoordinator.Callback<List<SyncConflictEntity>>() {
                    @Override
                    public void onSuccess(@NonNull List<SyncConflictEntity> value) {
                        updateSyncUi();
                        onInfoSnack(
                                R.string.sync_conflict_resolved,
                                null,
                                SnackBarInfo.Success,
                                Snackbar.LENGTH_LONG);
                        if (!unresolvedConflicts(value).isEmpty()) {
                            showNextConflictDialog();
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        finishSyncError(error);
                    }
                });
    }

    @NonNull
    private CharSequence resolveStatusText(
            boolean signedIn, SyncState state, int unresolvedConflicts) {
        if (!signedIn) {
            return getString(R.string.sync_sign_in_required);
        }
        if (state == null) {
            return getString(R.string.sync_status_ready);
        }
        if (state.getStatus() == SyncState.Status.SYNCING) {
            // A SYNCING state read back from storage can be the leftover of a run this process
            // never finished; reporting it forever would make the screen look stuck.
            return syncRunning
                    ? getString(R.string.sync_started)
                    : getString(R.string.sync_status_ready);
        }
        if (state.getStatus() == SyncState.Status.SUCCESS) {
            return unresolvedConflicts == 0
                    ? getString(R.string.sync_success)
                    : getString(R.string.sync_success_conflicts, unresolvedConflicts);
        }
        if (state.getStatus() == SyncState.Status.ERROR) {
            return getString(
                    R.string.sync_failed,
                    state.getErrorMessage() == null ? "Unknown" : state.getErrorMessage());
        }
        return getString(R.string.sync_status_ready);
    }

    @NonNull
    private CharSequence formatLastSync(@NonNull SyncState state) {
        if (state.getLastSuccessfulSyncAt() == null) {
            return getString(R.string.sync_last_sync_never);
        }
        String value =
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date.from(state.getLastSuccessfulSyncAt()));
        return getString(R.string.sync_last_sync_value, value);
    }

    @NonNull
    private String buildConflictMessage(@NonNull SyncConflictEntity conflict) {
        return getString(
                        R.string.sync_conflict_version,
                        getString(R.string.sync_conflict_local_label),
                        describeConflictPayload(
                                conflict.winnerSource.equals("LOCAL")
                                        ? conflict.winnerJson
                                        : conflict.loserJson))
                + "\n\n"
                + getString(
                        R.string.sync_conflict_version,
                        getString(R.string.sync_conflict_drive_label),
                        describeConflictPayload(
                                conflict.winnerSource.equals("REMOTE")
                                        ? conflict.winnerJson
                                        : conflict.loserJson));
    }

    @NonNull
    private String describeConflictPayload(@NonNull String recordJson) {
        try {
            JsonObject root = JsonParser.parseString(recordJson).getAsJsonObject();
            JsonElement deletedAt = root.get("deletedAt");
            if (deletedAt != null && !deletedAt.isJsonNull()) {
                return getString(R.string.sync_conflict_deleted);
            }
            JsonObject payload = root.getAsJsonObject("payload");
            if (payload == null) return getString(R.string.sync_conflict_deleted);
            if (payload.has("title") && !payload.get("title").isJsonNull()) {
                String title = payload.get("title").getAsString();
                if (!title.trim().isEmpty()) return title.trim();
            }
            if (payload.has("name") && !payload.get("name").isJsonNull()) {
                String name = payload.get("name").getAsString();
                if (!name.trim().isEmpty()) return name.trim();
            }
            if (payload.has("value") && !payload.get("value").isJsonNull()) {
                String value = payload.get("value").getAsString().trim();
                if (!value.isEmpty()) {
                    return value.length() > 120 ? value.substring(0, 120) + "…" : value;
                }
            }
        } catch (Exception ignored) {
        }
        return getString(R.string.sync_status_ready);
    }

    private int unresolvedConflictCount(@NonNull List<SyncConflictEntity> conflicts) {
        return unresolvedConflicts(conflicts).size();
    }

    @NonNull
    private List<SyncConflictEntity> unresolvedConflicts(
            @NonNull List<SyncConflictEntity> conflicts) {
        List<SyncConflictEntity> unresolved = new ArrayList<>();
        for (SyncConflictEntity conflict : conflicts) {
            if (!conflict.resolved) {
                unresolved.add(conflict);
            }
        }
        return unresolved;
    }

    // ---- AccountSyncFragment.Host ----

    @Override
    public void onAccountTabAttached(@NonNull AccountSyncFragment fragment) {
        accountTab = fragment;
        if (syncSetup == null) {
            fragment.showUnavailable();
            return;
        }
        updateSyncUi();
    }

    @Override
    public void onAccountTabDetached() {
        accountTab = null;
    }

    @Override
    public void onAccountSignInClicked() {
        if (syncSetup != null) onGoogleSignInClicked();
    }

    @Override
    public void onAccountSignOutClicked() {
        if (syncSetup != null) onGoogleSignInClicked();
    }

    @Override
    public void onAccountSyncClicked() {
        if (syncSetup != null) startSync();
    }

    @Override
    public void onAccountBackgroundSyncChanged(boolean enabled) {
        if (syncSetup != null) onBackgroundSyncToggled(enabled);
    }

    @Override
    public void onAccountConflictsClicked() {
        if (syncSetup != null) showNextConflictDialog();
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
                                    tab.setText(R.string.tab_account);
                                    break;
                                case 1:
                                    tab.setText(R.string.backup_and_export);
                                    break;
                                case 2:
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
        // onDestroy also runs on rotation, and a sync started here delivers its callback later.
        // Killing the executor then made that callback throw RejectedExecutionException.
        if (isFinishing()) {
            syncExecutor.shutdown();
        }
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
