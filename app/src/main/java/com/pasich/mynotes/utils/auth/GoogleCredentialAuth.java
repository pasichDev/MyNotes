package com.pasich.mynotes.utils.auth;

import android.content.Context;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

/** Google Sign-In authentication using Android Credential Manager. */
public final class GoogleCredentialAuth {

    public interface Callback {
        void onSuccess(@NonNull GoogleCredential credential);

        void onError(@NonNull Exception error);
    }

    public interface SignOutCallback {
        void onSuccess();

        void onError(@NonNull Exception error);
    }

    private final CredentialManager credentialManager;
    private final String serverClientId;
    private final java.util.concurrent.Executor executor;

    public GoogleCredentialAuth(@NonNull Context context, @NonNull String serverClientId) {
        if (serverClientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Google server client ID is required");
        }
        credentialManager = CredentialManager.create(context.getApplicationContext());
        this.serverClientId = serverClientId;
        executor = context.getMainExecutor();
    }

    /** Starts the explicit Sign in with Google flow. */
    public void signIn(@NonNull Context activityContext, @NonNull Callback callback) {
        GetSignInWithGoogleOption option =
                new GetSignInWithGoogleOption.Builder(serverClientId).build();
        GetCredentialRequest request =
                new GetCredentialRequest.Builder().addCredentialOption(option).build();
        try {
            credentialManager.getCredentialAsync(
                    activityContext,
                    request,
                    new CancellationSignal(),
                    executor,
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse response) {
                            try {
                                callback.onSuccess(toGoogleCredential(response.getCredential()));
                            } catch (Exception error) {
                                callback.onError(error);
                            }
                        }

                        @Override
                        public void onError(@NonNull GetCredentialException error) {
                            callback.onError(error);
                        }
                    });
        } catch (Exception error) {
            callback.onError(error);
        }
    }

    /** Clears the provider session; no token is retained by this class. */
    public void signOut(@NonNull SignOutCallback callback) {
        try {
            credentialManager.clearCredentialStateAsync(
                    new ClearCredentialStateRequest(),
                    new CancellationSignal(),
                    executor,
                    new CredentialManagerCallback<Void, ClearCredentialException>() {
                        @Override
                        public void onResult(Void ignored) {
                            callback.onSuccess();
                        }

                        @Override
                        public void onError(@NonNull ClearCredentialException error) {
                            callback.onError(error);
                        }
                    });
        } catch (Exception error) {
            callback.onError(error);
        }
    }

    @NonNull
    private static GoogleCredential toGoogleCredential(@NonNull Credential credential) {
        if (!(credential instanceof CustomCredential)) {
            throw new IllegalStateException("Unsupported credential type");
        }
        CustomCredential customCredential = (CustomCredential) credential;
        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(
                customCredential.getType())) {
            throw new IllegalStateException("Unsupported Google credential type");
        }
        try {
            GoogleIdTokenCredential googleCredential =
                    GoogleIdTokenCredential.createFrom(customCredential.getData());
            return new GoogleCredential(
                    googleCredential.getUniqueId(),
                    googleCredential.getEmail(),
                    googleCredential.getDisplayName(),
                    googleCredential.getIdToken());
        } catch (RuntimeException error) {
            throw new IllegalStateException("Invalid Google ID token credential", error);
        }
    }
}
