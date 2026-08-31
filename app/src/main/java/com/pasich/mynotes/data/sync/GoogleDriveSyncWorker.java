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
                return Result.retry();
            }
            SyncDependencies dependencies =
                    EntryPointAccessors.fromApplication(
                            getApplicationContext(), SyncDependencies.class);
            SyncState state =
                    new SyncService(
                                    new RoomSyncStore(
                                            getApplicationContext(),
                                            dependencies.database(),
                                            dependencies.preferenceHelper()))
                            .sync(new GoogleDriveSyncBackend(authorization.getAccessToken()));
            return state.getStatus() == SyncState.Status.SUCCESS
                    ? Result.success()
                    : Result.retry();
        } catch (Exception error) {
            return Result.retry();
        }
    }
}
