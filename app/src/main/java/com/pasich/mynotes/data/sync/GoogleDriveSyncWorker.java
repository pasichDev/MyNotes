package com.pasich.mynotes.data.sync;

import android.accounts.Account;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import dagger.hilt.android.EntryPointAccessors;
import java.util.Collections;

/** Periodic, network-constrained Drive sync using an already granted scope. */
public final class GoogleDriveSyncWorker extends Worker {
    private static final Scope DRIVE_FILE = new Scope("https://www.googleapis.com/auth/drive.file");

    public GoogleDriveSyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) return Result.success();
        try {
            AuthorizationRequest request =
                    new AuthorizationRequest.Builder()
                            .setRequestedScopes(Collections.singletonList(DRIVE_FILE))
                            .setAccount(new Account(user.getEmail(), "com.google"))
                            .build();
            AuthorizationResult authorization =
                    Tasks.await(
                            Identity.getAuthorizationClient(getApplicationContext())
                                    .authorize(request));
            if (authorization.hasResolution() || authorization.getAccessToken() == null) {
                return Result.failure();
            }
            SyncDependencies dependencies =
                    EntryPointAccessors.fromApplication(
                            getApplicationContext(), SyncDependencies.class);
            if (!dependencies.preferenceHelper().isSyncEnabled()
                    || !dependencies.preferenceHelper().isBackgroundSyncEnabled()) {
                return Result.success();
            }
            SyncState state =
                    new SyncService(
                                    new RoomSyncStore(
                                            getApplicationContext(),
                                            dependencies.database(),
                                            dependencies.preferenceHelper()))
                            .sync(new GoogleDriveSyncBackend(authorization.getAccessToken()));
            if (state.getStatus() == SyncState.Status.SUCCESS) return Result.success();
            return isRetryable(state.getErrorMessage()) ? Result.retry() : Result.failure();
        } catch (Exception error) {
            return isRetryable(error.getMessage()) ? Result.retry() : Result.failure();
        }
    }

    private static boolean isRetryable(String message) {
        if (message == null) return true;
        String value = message.toLowerCase(java.util.Locale.ROOT);
        return value.contains("offline")
                || value.contains("timeout")
                || value.contains("timed out")
                || value.contains("http 408")
                || value.contains("http 429")
                || value.contains("http 5")
                || value.contains("network")
                || value.contains("temporar");
    }
}
