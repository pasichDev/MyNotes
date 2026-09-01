package com.pasich.mynotes.ui.sync;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.entities.SyncConflictEntity;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import com.pasich.mynotes.data.sync.GoogleDriveSyncBackend;
import com.pasich.mynotes.data.sync.GoogleDriveSyncWorker;
import com.pasich.mynotes.data.sync.RoomSyncStore;
import com.pasich.mynotes.data.sync.SyncResolution;
import com.pasich.mynotes.data.sync.SyncService;
import com.pasich.mynotes.data.sync.SyncState;
import com.pasich.mynotes.utils.auth.FirebaseGoogleAuth;
import com.pasich.mynotes.utils.auth.GoogleCredentialAuth;
import com.pasich.mynotes.utils.auth.GoogleDriveAuthorization;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Builds a {@link SyncCoordinator} bound to one activity.
 *
 * <p>The wiring used to live inside BackupActivity, which made the account and sync state reachable
 * from that screen only. Extracting it lets the navigation drawer drive exactly the same
 * coordinator instead of growing a second, divergent implementation.
 */
public final class SyncCoordinatorFactory {

    private static final String BACKGROUND_SYNC_WORK_NAME = "mynotes-drive-sync";
    private static final long BACKGROUND_SYNC_INTERVAL_HOURS = 6;

    private SyncCoordinatorFactory() {
        // no instance
    }

    /** True when the build carries a Firebase configuration and sync can be offered at all. */
    public static boolean isConfigured(@NonNull Activity activity) {
        return !activity.getString(R.string.default_web_client_id).trim().isEmpty();
    }

    /** The authorization object has to be kept by the caller so it can forward activity results. */
    public static final class Result {
        private final SyncCoordinator coordinator;
        private final GoogleDriveAuthorization authorization;
        private final RoomSyncStore store;

        Result(
                SyncCoordinator coordinator,
                GoogleDriveAuthorization authorization,
                RoomSyncStore store) {
            this.coordinator = coordinator;
            this.authorization = authorization;
            this.store = store;
        }

        @NonNull
        public RoomSyncStore getStore() {
            return store;
        }

        @NonNull
        public SyncCoordinator getCoordinator() {
            return coordinator;
        }

        @NonNull
        public GoogleDriveAuthorization getAuthorization() {
            return authorization;
        }
    }

    /**
     * Returns null when the build has no Firebase configuration.
     *
     * <p>{@link GoogleCredentialAuth} rejects a blank client ID in its constructor, so callers must
     * not build a coordinator in that case; the sync UI is hidden instead.
     */
    @Nullable
    public static Result create(
            @NonNull Activity activity,
            @NonNull AppDatabase database,
            @NonNull PreferenceHelper preferenceHelper,
            @NonNull FirebaseGoogleAuth firebaseGoogleAuth,
            @NonNull Executor backgroundExecutor) {
        if (!isConfigured(activity)) {
            return null;
        }
        GoogleCredentialAuth credentialAuth =
                new GoogleCredentialAuth(
                        activity, activity.getString(R.string.default_web_client_id));
        GoogleDriveAuthorization authorization = new GoogleDriveAuthorization(activity);
        RoomSyncStore store = new RoomSyncStore(activity, database, preferenceHelper);

        SyncCoordinator coordinator =
                new SyncCoordinator(
                        preferenceHelper,
                        firebaseGoogleAuth,
                        credentialAuth,
                        authorization,
                        new SyncCoordinator.ConflictStore() {
                            @NonNull
                            @Override
                            public SyncState readState() {
                                try {
                                    return store.readState();
                                } catch (IOException ignored) {
                                    return SyncState.idle();
                                }
                            }

                            @NonNull
                            @Override
                            public List<SyncConflictEntity> getConflicts() {
                                return store.getConflicts();
                            }

                            @Override
                            public void resolveConflict(
                                    long conflictId, @NonNull SyncResolution resolution)
                                    throws IOException {
                                store.resolveConflict(conflictId, resolution);
                            }

                            @NonNull
                            @Override
                            public SyncState sync(@NonNull String accessToken) {
                                return new SyncService(store)
                                        .sync(new GoogleDriveSyncBackend(accessToken));
                            }
                        },
                        new SyncCoordinator.BackgroundScheduler() {
                            @Override
                            public void enable() {
                                enableBackgroundSync(activity);
                            }

                            @Override
                            public void disable() {
                                disableBackgroundSync(activity);
                            }
                        },
                        backgroundExecutor,
                        activity::runOnUiThread);
        return new Result(coordinator, authorization, store);
    }

    private static void enableBackgroundSync(Activity activity) {
        Constraints constraints =
                new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build();
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                                GoogleDriveSyncWorker.class,
                                BACKGROUND_SYNC_INTERVAL_HOURS,
                                TimeUnit.HOURS)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                        .build();
        WorkManager.getInstance(activity.getApplicationContext())
                .enqueueUniquePeriodicWork(
                        BACKGROUND_SYNC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    private static void disableBackgroundSync(Activity activity) {
        WorkManager.getInstance(activity.getApplicationContext())
                .cancelUniqueWork(BACKGROUND_SYNC_WORK_NAME);
    }
}
