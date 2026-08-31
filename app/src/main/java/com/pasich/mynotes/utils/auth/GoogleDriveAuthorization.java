package com.pasich.mynotes.utils.auth;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import androidx.annotation.NonNull;
import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import java.util.Collections;

/** Requests a short-lived Google Drive file access token for the signed-in account. */
public final class GoogleDriveAuthorization {
    public static final int REQUEST_CODE = 781;
    private static final Scope DRIVE_FILE = new Scope("https://www.googleapis.com/auth/drive.file");

    public interface Callback {
        void onAuthorized(@NonNull String accessToken);

        void onError(@NonNull Exception error);
    }

    private final AuthorizationClient client;
    private Callback pendingCallback;

    public GoogleDriveAuthorization(@NonNull Activity activity) {
        client = Identity.getAuthorizationClient(activity);
    }

    public void authorize(@NonNull Activity activity, @NonNull Callback callback) {
        pendingCallback = callback;
        AuthorizationRequest request =
                new AuthorizationRequest.Builder()
                        .setRequestedScopes(Collections.singletonList(DRIVE_FILE))
                        .build();
        Task<AuthorizationResult> task = client.authorize(request);
        task.addOnSuccessListener(
                result -> {
                    if (result.hasResolution()) {
                        try {
                            activity.startIntentSenderForResult(
                                    result.getPendingIntent().getIntentSender(),
                                    REQUEST_CODE,
                                    null,
                                    0,
                                    0,
                                    0);
                        } catch (IntentSender.SendIntentException error) {
                            finishWithError(error);
                        }
                    } else {
                        finishWithToken(result);
                    }
                });
        task.addOnFailureListener(this::finishWithError);
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE) return false;
        if (resultCode != Activity.RESULT_OK || data == null) {
            finishWithError(new IllegalStateException("Google Drive authorization was cancelled"));
            return true;
        }
        try {
            finishWithToken(client.getAuthorizationResultFromIntent(data));
        } catch (Exception error) {
            finishWithError(error);
        }
        return true;
    }

    private void finishWithToken(AuthorizationResult result) {
        Callback callback = pendingCallback;
        pendingCallback = null;
        String token = result.getAccessToken();
        if (callback == null) return;
        if (token == null || token.trim().isEmpty()) {
            callback.onError(new IllegalStateException("Google Drive returned no access token"));
        } else {
            callback.onAuthorized(token);
        }
    }

    private void finishWithError(Exception error) {
        Callback callback = pendingCallback;
        pendingCallback = null;
        if (callback != null) callback.onError(error);
    }
}
