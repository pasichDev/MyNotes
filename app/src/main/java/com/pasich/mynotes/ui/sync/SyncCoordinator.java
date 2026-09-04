package com.pasich.mynotes.ui.sync;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.auth.FirebaseUser;
import com.pasich.mynotes.data.database.entities.SyncConflictEntity;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import com.pasich.mynotes.data.sync.SyncResolution;
import com.pasich.mynotes.data.sync.SyncRollout;
import com.pasich.mynotes.data.sync.SyncState;
import com.pasich.mynotes.utils.auth.FirebaseGoogleAuth;
import com.pasich.mynotes.utils.auth.GoogleCredential;
import com.pasich.mynotes.utils.auth.GoogleCredentialAuth;
import com.pasich.mynotes.utils.auth.GoogleDriveAuthorization;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** Coordinates connect/disconnect/manual-sync flows so BackupActivity stays presentation-only. */
public final class SyncCoordinator {

    private static final String TAG = "SyncCoordinator";

    public interface Callback<T> {
        void onSuccess(@NonNull T value);

        void onError(@NonNull Exception error);
    }

    public interface ConflictStore {
        @NonNull
        SyncState readState() throws IOException;

        @NonNull
        List<SyncConflictEntity> getConflicts();

        void resolveConflict(long conflictId, @NonNull SyncResolution resolution)
                throws IOException;

        @NonNull
        SyncState sync(@NonNull String accessToken);

        /** Drops everything tied to the account being disconnected. */
        void clearAfterDisconnect();
    }

    public interface BackgroundScheduler {
        void enable();

        void disable();
    }

    public static final class Profile {
        private final boolean signedIn;
        private final String displayName;
        private final String email;

        Profile(boolean signedIn, @Nullable String displayName, @Nullable String email) {
            this.signedIn = signedIn;
            this.displayName = displayName;
            this.email = email;
        }

        public boolean isSignedIn() {
            return signedIn;
        }

        @NonNull
        public String getDisplayName() {
            return displayName == null || displayName.trim().isEmpty()
                    ? "Google"
                    : displayName.trim();
        }

        @NonNull
        public String getEmail() {
            return email == null ? "" : email;
        }

        @NonNull
        public String getAvatarLabel() {
            return getDisplayName().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        }
    }

    private final PreferenceHelper preferenceHelper;
    private final FirebaseGoogleAuth firebaseGoogleAuth;
    private final GoogleCredentialAuth googleCredentialAuth;
    private final GoogleDriveAuthorization googleDriveAuthorization;
    private final ConflictStore conflictStore;
    private final BackgroundScheduler backgroundScheduler;
    private final Executor workerExecutor;
    private final Executor mainExecutor;

    public SyncCoordinator(
            @NonNull PreferenceHelper preferenceHelper,
            @NonNull FirebaseGoogleAuth firebaseGoogleAuth,
            @NonNull GoogleCredentialAuth googleCredentialAuth,
            @NonNull GoogleDriveAuthorization googleDriveAuthorization,
            @NonNull ConflictStore conflictStore,
            @NonNull BackgroundScheduler backgroundScheduler,
            @NonNull Executor workerExecutor,
            @NonNull Executor mainExecutor) {
        this.preferenceHelper = Objects.requireNonNull(preferenceHelper, "preferenceHelper");
        this.firebaseGoogleAuth = Objects.requireNonNull(firebaseGoogleAuth, "firebaseGoogleAuth");
        this.googleCredentialAuth =
                Objects.requireNonNull(googleCredentialAuth, "googleCredentialAuth");
        this.googleDriveAuthorization =
                Objects.requireNonNull(googleDriveAuthorization, "googleDriveAuthorization");
        this.conflictStore = Objects.requireNonNull(conflictStore, "conflictStore");
        this.backgroundScheduler =
                Objects.requireNonNull(backgroundScheduler, "backgroundScheduler");
        this.workerExecutor = Objects.requireNonNull(workerExecutor, "workerExecutor");
        this.mainExecutor = Objects.requireNonNull(mainExecutor, "mainExecutor");
    }

    @NonNull
    public Profile getProfile() {
        FirebaseUser user = firebaseGoogleAuth.getCurrentUser();
        if (user == null) {
            return new Profile(false, null, null);
        }
        return new Profile(true, user.getDisplayName(), user.getEmail());
    }

    public boolean isBackgroundSyncEnabled() {
        return preferenceHelper.isBackgroundSyncEnabled();
    }

    /**
     * Whether the user has consented to the first upload for the currently connected account.
     *
     * <p>The screen must decide whether to ask from this flag, not from a stored "last successful
     * sync" timestamp: {@link #disconnect} clears the flag but the timestamp is durable, so the two
     * disagreed after any sign-out and the consent dialog became unreachable while {@link #syncNow}
     * kept refusing to run.
     */
    public boolean isFirstSyncConfirmed() {
        return preferenceHelper.isFirstSyncConfirmed();
    }

    @NonNull
    public SyncState getLastState() {
        try {
            return conflictStore.readState();
        } catch (IOException ignored) {
            return SyncState.idle();
        }
    }

    @NonNull
    public List<SyncConflictEntity> getConflicts() {
        List<SyncConflictEntity> conflicts = conflictStore.getConflicts();
        return conflicts == null ? Collections.emptyList() : conflicts;
    }

    public void setBackgroundSyncEnabled(boolean enabled) {
        preferenceHelper.setBackgroundSyncEnabled(enabled);
        if (enabled && preferenceHelper.isFirstSyncConfirmed() && firebaseGoogleAuth.isSignedIn()) {
            backgroundScheduler.enable();
        } else {
            backgroundScheduler.disable();
        }
    }

    /** Records the user's first-sync consent before any content can leave the device. */
    public void confirmFirstSync() {
        preferenceHelper.setFirstSyncConfirmed(true);
        if (preferenceHelper.isBackgroundSyncEnabled() && firebaseGoogleAuth.isSignedIn()) {
            backgroundScheduler.enable();
        }
    }

    public void connect(@NonNull Activity activity, @NonNull Callback<Profile> callback) {
        googleCredentialAuth.signIn(
                activity,
                new GoogleCredentialAuth.Callback() {
                    @Override
                    public void onSuccess(@NonNull GoogleCredential credential) {
                        firebaseGoogleAuth.signIn(
                                credential,
                                new FirebaseGoogleAuth.Callback() {
                                    @Override
                                    public void onSuccess(@NonNull FirebaseUser user) {
                                        SyncRollout.ensureBucket(preferenceHelper);
                                        preferenceHelper.setSyncEnabled(true);
                                        if (preferenceHelper.isBackgroundSyncEnabled()
                                                && preferenceHelper.isFirstSyncConfirmed()) {
                                            backgroundScheduler.enable();
                                        }
                                        deliverSuccess(callback, getProfile());
                                    }

                                    @Override
                                    public void onError(@NonNull Exception error) {
                                        deliverError(callback, error);
                                    }
                                });
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        deliverError(callback, error);
                    }
                });
    }

    public void disconnect(@NonNull Callback<Profile> callback) {
        firebaseGoogleAuth.signOut();
        preferenceHelper.setSyncEnabled(false);
        preferenceHelper.setBackgroundSyncEnabled(false);
        preferenceHelper.setFirstSyncConfirmed(false);
        backgroundScheduler.disable();
        // The stored sync state, the conflict queue and the downloaded blob cache all describe the
        // account being disconnected. Leaving them behind also left a lastSuccessfulSyncAt that
        // made the next connection look like it had already synced.
        //
        // Off the main thread: this runs from a button tap and Room refuses main-thread access.
        // A failure here must not take the sign-out down with it, so it is logged, not propagated.
        runOnWorker(
                callback,
                () -> {
                    try {
                        conflictStore.clearAfterDisconnect();
                    } catch (Exception error) {
                        Log.w(TAG, "Could not clear sync state after disconnect", error);
                    }
                });
        googleCredentialAuth.signOut(
                new GoogleCredentialAuth.SignOutCallback() {
                    @Override
                    public void onSuccess() {
                        deliverSuccess(callback, getProfile());
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        deliverSuccess(callback, getProfile());
                    }
                });
    }

    public void syncNow(@NonNull Activity activity, @NonNull Callback<SyncState> callback) {
        if (!firebaseGoogleAuth.isSignedIn()) {
            deliverError(callback, new IllegalStateException("Google sign-in is required"));
            return;
        }
        if (!preferenceHelper.isFirstSyncConfirmed()) {
            deliverError(
                    callback,
                    new IllegalStateException("Confirm the first sync before continuing"));
            return;
        }
        if (!SyncRollout.isWithinRollout(preferenceHelper)) {
            deliverError(
                    callback, new IllegalStateException("Sync is not available in this rollout"));
            return;
        }
        googleDriveAuthorization.authorize(
                activity,
                new GoogleDriveAuthorization.Callback() {
                    @Override
                    public void onAuthorized(@NonNull String accessToken) {
                        runOnWorker(
                                callback,
                                () -> {
                                    try {
                                        preferenceHelper.setSyncEnabled(true);
                                        SyncState result = conflictStore.sync(accessToken);
                                        if (result.getStatus() == SyncState.Status.SUCCESS
                                                && preferenceHelper.isBackgroundSyncEnabled()) {
                                            backgroundScheduler.enable();
                                        }
                                        deliverSuccess(callback, result);
                                    } catch (Exception error) {
                                        deliverError(callback, error);
                                    }
                                });
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        deliverError(callback, error);
                    }
                });
    }

    public boolean onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        return googleDriveAuthorization.onActivityResult(requestCode, resultCode, data);
    }

    public void resolveConflict(
            long conflictId,
            @NonNull SyncResolution resolution,
            @NonNull Callback<List<SyncConflictEntity>> callback) {
        runOnWorker(
                callback,
                () -> {
                    try {
                        conflictStore.resolveConflict(conflictId, resolution);
                        deliverSuccess(callback, getConflicts());
                    } catch (Exception error) {
                        deliverError(callback, error);
                    }
                });
    }

    /**
     * Submits background work without letting a gone screen crash the app.
     *
     * <p>The executor belongs to the activity that built this coordinator, so a callback arriving
     * after that activity finished used to hit a terminated pool and throw {@link
     * RejectedExecutionException} from whatever thread delivered it. Checking {@code isShutdown()}
     * first cannot close the race; catching the rejection can.
     */
    private void runOnWorker(@NonNull Callback<?> callback, @NonNull Runnable task) {
        try {
            workerExecutor.execute(task);
        } catch (RejectedExecutionException rejected) {
            Log.w(TAG, "Sync work could not be scheduled; the screen is gone", rejected);
            deliverError(callback, rejected);
        }
    }

    private <T> void deliverSuccess(@NonNull Callback<T> callback, @NonNull T value) {
        postToMain(() -> callback.onSuccess(value));
    }

    private void deliverError(@NonNull Callback<?> callback, @NonNull Exception error) {
        postToMain(() -> callback.onError(error));
    }

    /**
     * Hands the result to the main executor, which owns the decision to drop it.
     *
     * <p>Whether delivery to a destroyed screen is safe depends entirely on the executor that was
     * injected — an {@code Activity::runOnUiThread} method reference posts to a handler and never
     * rejects, so it would happily run the task against destroyed views. {@code
     * SyncCoordinatorFactory} supplies a lifecycle-aware executor for that reason; the catch below
     * only covers an executor that shuts down instead.
     */
    private void postToMain(@NonNull Runnable task) {
        try {
            mainExecutor.execute(task);
        } catch (RejectedExecutionException rejected) {
            Log.w(TAG, "Sync result could not be delivered; the screen is gone", rejected);
        }
    }
}
