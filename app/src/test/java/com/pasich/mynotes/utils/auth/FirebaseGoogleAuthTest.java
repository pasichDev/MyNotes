package com.pasich.mynotes.utils.auth;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class FirebaseGoogleAuthTest {

    @Test
    public void signInWithCompletedTaskReturnsCurrentFirebaseUser() {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        @SuppressWarnings("unchecked")
        Task<AuthResult> task = mock(Task.class);
        FirebaseUser user = mock(FirebaseUser.class);
        when(firebaseAuth.signInWithCredential(any())).thenReturn(task);
        when(task.isSuccessful()).thenReturn(true);
        when(firebaseAuth.getCurrentUser()).thenReturn(user);

        FirebaseGoogleAuth.Callback callback = mock(FirebaseGoogleAuth.Callback.class);
        new FirebaseGoogleAuth(firebaseAuth)
                .signIn(
                        new GoogleCredential("id", "user@example.com", "User", "id-token"),
                        callback);

        ArgumentCaptor<OnCompleteListener<AuthResult>> listenerCaptor =
                ArgumentCaptor.forClass(OnCompleteListener.class);
        verify(task).addOnCompleteListener(listenerCaptor.capture());
        listenerCaptor.getValue().onComplete(task);

        verify(callback).onSuccess(user);
    }

    @Test
    public void signInFailureReturnsFirebaseException() {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        @SuppressWarnings("unchecked")
        Task<AuthResult> task = mock(Task.class);
        Exception failure = new IllegalStateException("no account");
        when(firebaseAuth.signInWithCredential(any())).thenReturn(task);
        when(task.isSuccessful()).thenReturn(false);
        when(task.getException()).thenReturn(failure);

        FirebaseGoogleAuth.Callback callback = mock(FirebaseGoogleAuth.Callback.class);
        new FirebaseGoogleAuth(firebaseAuth)
                .signIn(
                        new GoogleCredential("id", "user@example.com", "User", "id-token"),
                        callback);

        ArgumentCaptor<OnCompleteListener<AuthResult>> listenerCaptor =
                ArgumentCaptor.forClass(OnCompleteListener.class);
        verify(task).addOnCompleteListener(listenerCaptor.capture());
        listenerCaptor.getValue().onComplete(task);

        verify(callback).onError(failure);
    }

    @Test
    public void signOutDelegatesToFirebaseAndClearsSignedInState() {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        FirebaseGoogleAuth auth = new FirebaseGoogleAuth(firebaseAuth);
        when(firebaseAuth.getCurrentUser()).thenReturn(null);

        auth.signOut();

        verify(firebaseAuth).signOut();
        assertThat(auth.isSignedIn()).isFalse();
        assertThat(auth.getCurrentUser()).isNull();
    }
}
