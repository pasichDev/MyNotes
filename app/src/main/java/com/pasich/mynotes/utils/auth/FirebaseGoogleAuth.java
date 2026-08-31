package com.pasich.mynotes.utils.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Exchanges a Google ID token obtained by {@link GoogleCredentialAuth} for a Firebase session.
 * Firebase persists the session securely; this class never stores the Google ID token.
 */
@Singleton
public final class FirebaseGoogleAuth {

    public interface Callback {
        void onSuccess(@NonNull FirebaseUser user);

        void onError(@NonNull Exception error);
    }

    private final FirebaseAuth firebaseAuth;

    @Inject
    public FirebaseGoogleAuth(@NonNull FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    /** Starts a Firebase sign-in using the short-lived ID token from Credential Manager. */
    public void signIn(@NonNull GoogleCredential googleCredential, @NonNull Callback callback) {
        try {
            AuthCredential firebaseCredential =
                    GoogleAuthProvider.getCredential(googleCredential.getIdToken(), null);
            Task<AuthResult> signInTask = firebaseAuth.signInWithCredential(firebaseCredential);
            signInTask.addOnCompleteListener(
                    task -> {
                        if (!task.isSuccessful()) {
                            callback.onError(
                                    task.getException() != null
                                            ? task.getException()
                                            : new IllegalStateException("Firebase sign-in failed"));
                            return;
                        }

                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user == null) {
                            callback.onError(
                                    new IllegalStateException(
                                            "Firebase sign-in completed without an authenticated user"));
                            return;
                        }
                        callback.onSuccess(user);
                    });
        } catch (Exception error) {
            callback.onError(error);
        }
    }

    /** Returns the active Firebase user, or {@code null} when the app is signed out. */
    @Nullable
    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    public boolean isSignedIn() {
        return getCurrentUser() != null;
    }

    /** Ends the Firebase session. Also clear Credential Manager state with GoogleCredentialAuth. */
    public void signOut() {
        firebaseAuth.signOut();
    }
}
