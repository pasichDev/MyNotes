package com.pasich.mynotes.ui.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import android.app.Activity;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseUser;
import com.pasich.mynotes.data.database.entities.SyncConflictEntity;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import com.pasich.mynotes.data.sync.SyncResolution;
import com.pasich.mynotes.data.sync.SyncState;
import com.pasich.mynotes.utils.auth.FirebaseGoogleAuth;
import com.pasich.mynotes.utils.auth.GoogleCredential;
import com.pasich.mynotes.utils.auth.GoogleCredentialAuth;
import com.pasich.mynotes.utils.auth.GoogleDriveAuthorization;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.mockito.Mockito;

public class SyncCoordinatorTest {
    private final Executor directExecutor = Runnable::run;

    @Test
    public void setBackgroundSyncEnabled_requiresSignedInSessionToSchedule() {
        FakePreferenceHelper preferences = new FakePreferenceHelper();
        FakeScheduler scheduler = new FakeScheduler();
        SyncCoordinator coordinator =
                new SyncCoordinator(
                        preferences,
                        firebaseAuth(null),
                        mock(GoogleCredentialAuth.class),
                        mock(GoogleDriveAuthorization.class),
                        new FakeConflictStore(),
                        scheduler,
                        directExecutor,
                        directExecutor);

        coordinator.setBackgroundSyncEnabled(true);

        assertThat(preferences.backgroundEnabled).isTrue();
        assertThat(scheduler.enableCalls).isEqualTo(0);
        assertThat(scheduler.disableCalls).isEqualTo(1);
    }

    @Test
    public void backgroundSync_waitsForFirstSyncConfirmation() {
        FakePreferenceHelper preferences = new FakePreferenceHelper();
        FakeScheduler scheduler = new FakeScheduler();
        SyncCoordinator coordinator =
                new SyncCoordinator(
                        preferences,
                        firebaseAuth(mock(FirebaseUser.class)),
                        mock(GoogleCredentialAuth.class),
                        mock(GoogleDriveAuthorization.class),
                        new FakeConflictStore(),
                        scheduler,
                        directExecutor,
                        directExecutor);

        coordinator.setBackgroundSyncEnabled(true);
        coordinator.confirmFirstSync();

        assertThat(preferences.firstSyncConfirmed).isTrue();
        assertThat(scheduler.enableCalls).isEqualTo(1);
    }

    @Test
    public void getLastState_returnsIdleWhenStoredSyncStateCannotBeRead() {
        FakeConflictStore store = new FakeConflictStore();
        store.readStateError = new IOException("unreadable sync state");
        SyncCoordinator coordinator =
                new SyncCoordinator(
                        new FakePreferenceHelper(),
                        firebaseAuth(null),
                        mock(GoogleCredentialAuth.class),
                        mock(GoogleDriveAuthorization.class),
                        store,
                        new FakeScheduler(),
                        directExecutor,
                        directExecutor);

        assertThat(coordinator.getLastState().getStatus()).isEqualTo(SyncState.Status.IDLE);
    }

    @Test
    public void connect_marksSyncEnabledAndReturnsProfile() {
        FakePreferenceHelper preferences = new FakePreferenceHelper();
        FakeScheduler scheduler = new FakeScheduler();
        FirebaseUser user = mock(FirebaseUser.class);
        Mockito.when(user.getDisplayName()).thenReturn("Pasich");
        Mockito.when(user.getEmail()).thenReturn("me@example.com");
        FirebaseGoogleAuth firebaseAuth = firebaseAuth(user);
        GoogleCredentialAuth credentialAuth = mock(GoogleCredentialAuth.class);
        Mockito.doAnswer(
                        invocation -> {
                            GoogleCredentialAuth.Callback callback = invocation.getArgument(1);
                            callback.onSuccess(new GoogleCredential("id", "mail", "Name", "token"));
                            return null;
                        })
                .when(credentialAuth)
                .signIn(
                        Mockito.any(Activity.class),
                        Mockito.any(GoogleCredentialAuth.Callback.class));

        SyncCoordinator coordinator =
                new SyncCoordinator(
                        preferences,
                        firebaseAuth,
                        credentialAuth,
                        mock(GoogleDriveAuthorization.class),
                        new FakeConflictStore(),
                        scheduler,
                        directExecutor,
                        directExecutor);

        CapturingCallback<SyncCoordinator.Profile> callback = new CapturingCallback<>();
        coordinator.connect(mock(Activity.class), callback);

        assertThat(preferences.syncEnabled).isTrue();
        assertThat(callback.error).isNull();
        assertThat(callback.value.getDisplayName()).isEqualTo("Pasich");
        assertThat(callback.value.getEmail()).isEqualTo("me@example.com");
    }

    @Test
    public void syncNow_runsStoreAndEnablesSchedulerOnlyWhenSuccessful() {
        FakePreferenceHelper preferences = new FakePreferenceHelper();
        preferences.backgroundEnabled = true;
        preferences.firstSyncConfirmed = true;
        FakeScheduler scheduler = new FakeScheduler();
        FakeConflictStore store = new FakeConflictStore();
        store.state = SyncState.success("google-drive", Instant.parse("2026-08-31T12:00:00Z"), 1);
        GoogleDriveAuthorization authorization = mock(GoogleDriveAuthorization.class);
        Mockito.doAnswer(
                        invocation -> {
                            GoogleDriveAuthorization.Callback callback = invocation.getArgument(1);
                            callback.onAuthorized("access-token");
                            return null;
                        })
                .when(authorization)
                .authorize(
                        Mockito.any(Activity.class),
                        Mockito.any(GoogleDriveAuthorization.Callback.class));

        SyncCoordinator coordinator =
                new SyncCoordinator(
                        preferences,
                        firebaseAuth(mock(FirebaseUser.class)),
                        mock(GoogleCredentialAuth.class),
                        authorization,
                        store,
                        scheduler,
                        directExecutor,
                        directExecutor);

        CapturingCallback<SyncState> callback = new CapturingCallback<>();
        coordinator.syncNow(mock(Activity.class), callback);

        assertThat(store.lastToken).isEqualTo("access-token");
        assertThat(scheduler.enableCalls).isEqualTo(1);
        assertThat(callback.value.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
    }

    @Test
    public void syncNow_requiresFirstSyncConfirmationBeforeAuthorizing() {
        FakePreferenceHelper preferences = new FakePreferenceHelper();
        GoogleDriveAuthorization authorization = mock(GoogleDriveAuthorization.class);
        SyncCoordinator coordinator =
                new SyncCoordinator(
                        preferences,
                        firebaseAuth(mock(FirebaseUser.class)),
                        mock(GoogleCredentialAuth.class),
                        authorization,
                        new FakeConflictStore(),
                        new FakeScheduler(),
                        directExecutor,
                        directExecutor);

        CapturingCallback<SyncState> callback = new CapturingCallback<>();
        coordinator.syncNow(mock(Activity.class), callback);

        assertThat(callback.value).isNull();
        assertThat(callback.error).hasMessageThat().contains("Confirm the first sync");
        Mockito.verifyNoInteractions(authorization);
    }

    @Test
    public void disconnect_clearsConsentAndStoredStateTogether() {
        // The screen asks for first-sync consent when isFirstSyncConfirmed() is false, and
        // syncNow() refuses while it is false. Both must flip together on a sign-out, and the
        // durable state has to go with them: leaving a lastSuccessfulSyncAt behind is what used to
        // make the next connection look already-synced, skipping a dialog that alone could restore
        // the consent flag. Manual and background sync were then both dead until app data reset.
        FakePreferenceHelper preferences = new FakePreferenceHelper();
        preferences.firstSyncConfirmed = true;
        preferences.syncEnabled = true;
        preferences.backgroundEnabled = true;
        FakeConflictStore store = new FakeConflictStore();
        store.state = SyncState.success("google-drive", Instant.parse("2026-09-01T12:00:00Z"), 0);
        GoogleCredentialAuth credentialAuth = mock(GoogleCredentialAuth.class);
        Mockito.doAnswer(
                        invocation -> {
                            GoogleCredentialAuth.SignOutCallback callback =
                                    invocation.getArgument(0);
                            callback.onSuccess();
                            return null;
                        })
                .when(credentialAuth)
                .signOut(Mockito.any(GoogleCredentialAuth.SignOutCallback.class));
        FakeScheduler scheduler = new FakeScheduler();
        SyncCoordinator coordinator =
                new SyncCoordinator(
                        preferences,
                        firebaseAuth(mock(FirebaseUser.class)),
                        credentialAuth,
                        mock(GoogleDriveAuthorization.class),
                        store,
                        scheduler,
                        directExecutor,
                        directExecutor);

        coordinator.disconnect(new CapturingCallback<>());

        assertThat(coordinator.isFirstSyncConfirmed()).isFalse();
        assertThat(store.clearCalls).isEqualTo(1);
        assertThat(coordinator.getLastState().getLastSuccessfulSyncAt()).isNull();
        assertThat(scheduler.disableCalls).isAtLeast(1);
    }

    @Test
    public void syncNow_allowsAnExplicitlyEnabledUser() {
        FakePreferenceHelper preferences = new FakePreferenceHelper();
        preferences.firstSyncConfirmed = true;
        GoogleDriveAuthorization authorization = mock(GoogleDriveAuthorization.class);
        Mockito.doAnswer(
                        invocation -> {
                            GoogleDriveAuthorization.Callback callback = invocation.getArgument(1);
                            callback.onAuthorized("access-token");
                            return null;
                        })
                .when(authorization)
                .authorize(
                        Mockito.any(Activity.class),
                        Mockito.any(GoogleDriveAuthorization.Callback.class));
        FakeConflictStore store = new FakeConflictStore();
        store.state = SyncState.success("google-drive", Instant.parse("2026-09-01T12:00:00Z"), 1);
        SyncCoordinator coordinator =
                new SyncCoordinator(
                        preferences,
                        firebaseAuth(mock(FirebaseUser.class)),
                        mock(GoogleCredentialAuth.class),
                        authorization,
                        store,
                        new FakeScheduler(),
                        directExecutor,
                        directExecutor);

        CapturingCallback<SyncState> callback = new CapturingCallback<>();
        coordinator.syncNow(mock(Activity.class), callback);

        assertThat(store.lastToken).isEqualTo("access-token");
        assertThat(callback.error).isNull();
        assertThat(callback.value.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
    }

    @Test
    public void syncNow_doesNotRequireAFeatureRollout() {
        FakePreferenceHelper preferences = new FakePreferenceHelper();
        preferences.firstSyncConfirmed = true;
        GoogleDriveAuthorization authorization = mock(GoogleDriveAuthorization.class);
        Mockito.doAnswer(
                        invocation -> {
                            GoogleDriveAuthorization.Callback callback = invocation.getArgument(1);
                            callback.onAuthorized("access-token");
                            return null;
                        })
                .when(authorization)
                .authorize(
                        Mockito.any(Activity.class),
                        Mockito.any(GoogleDriveAuthorization.Callback.class));
        FakeConflictStore store = new FakeConflictStore();
        store.state = SyncState.success("google-drive", Instant.parse("2026-09-01T12:00:00Z"), 1);
        SyncCoordinator coordinator =
                new SyncCoordinator(
                        preferences,
                        firebaseAuth(mock(FirebaseUser.class)),
                        mock(GoogleCredentialAuth.class),
                        authorization,
                        store,
                        new FakeScheduler(),
                        directExecutor,
                        directExecutor);

        CapturingCallback<SyncState> callback = new CapturingCallback<>();
        coordinator.syncNow(mock(Activity.class), callback);

        assertThat(store.lastToken).isEqualTo("access-token");
        assertThat(callback.error).isNull();
    }

    @Test
    public void resolveConflict_updatesStoreAndReturnsLatestConflicts() {
        FakePreferenceHelper preferences = new FakePreferenceHelper();
        FakeConflictStore store = new FakeConflictStore();
        store.conflicts.add(
                new SyncConflictEntity(
                        "note",
                        "550e8400-e29b-41d4-a716-446655440000",
                        "test-version-pair",
                        "LOCAL",
                        "{}",
                        "{}",
                        1L,
                        2L,
                        false,
                        false,
                        SyncResolution.PENDING.name(),
                        false,
                        3L,
                        0L));
        SyncCoordinator coordinator =
                new SyncCoordinator(
                        preferences,
                        firebaseAuth(mock(FirebaseUser.class)),
                        mock(GoogleCredentialAuth.class),
                        mock(GoogleDriveAuthorization.class),
                        store,
                        new FakeScheduler(),
                        directExecutor,
                        directExecutor);

        CapturingCallback<List<SyncConflictEntity>> callback = new CapturingCallback<>();
        coordinator.resolveConflict(0L, SyncResolution.KEEP_DRIVE, callback);

        assertThat(store.resolutions).containsExactly("0:KEEP_DRIVE");
        assertThat(callback.value).hasSize(1);
    }

    private static FirebaseGoogleAuth firebaseAuth(FirebaseUser user) {
        FirebaseGoogleAuth auth = mock(FirebaseGoogleAuth.class);
        Mockito.when(auth.getCurrentUser()).thenReturn(user);
        Mockito.when(auth.isSignedIn()).thenReturn(user != null);
        Mockito.doAnswer(
                        invocation -> {
                            com.pasich.mynotes.utils.auth.GoogleCredential credential =
                                    invocation.getArgument(0);
                            FirebaseGoogleAuth.Callback callback = invocation.getArgument(1);
                            callback.onSuccess(user != null ? user : mock(FirebaseUser.class));
                            return null;
                        })
                .when(auth)
                .signIn(
                        Mockito.any(com.pasich.mynotes.utils.auth.GoogleCredential.class),
                        Mockito.any(FirebaseGoogleAuth.Callback.class));
        return auth;
    }

    private static final class FakeScheduler implements SyncCoordinator.BackgroundScheduler {
        private int enableCalls;
        private int disableCalls;

        @Override
        public void enable() {
            enableCalls++;
        }

        @Override
        public void disable() {
            disableCalls++;
        }
    }

    private static final class FakeConflictStore implements SyncCoordinator.ConflictStore {
        private SyncState state = SyncState.idle();
        private IOException readStateError;
        private final List<SyncConflictEntity> conflicts = new ArrayList<>();
        private final List<String> resolutions = new ArrayList<>();
        private String lastToken;
        private int clearCalls;

        @NonNull
        @Override
        public SyncState readState() throws IOException {
            if (readStateError != null) {
                throw readStateError;
            }
            return state;
        }

        @NonNull
        @Override
        public List<SyncConflictEntity> getConflicts() {
            return conflicts;
        }

        @Override
        public void resolveConflict(long conflictId, @NonNull SyncResolution resolution) {
            resolutions.add(conflictId + ":" + resolution.name());
        }

        @NonNull
        @Override
        public SyncState sync(@NonNull String accessToken) {
            lastToken = accessToken;
            return state;
        }

        @Override
        public void clearAfterDisconnect() {
            clearCalls++;
            state = SyncState.idle();
            conflicts.clear();
        }
    }

    private static final class CapturingCallback<T> implements SyncCoordinator.Callback<T> {
        private T value;
        private Exception error;

        @Override
        public void onSuccess(@NonNull T value) {
            this.value = value;
        }

        @Override
        public void onError(@NonNull Exception error) {
            this.error = error;
        }
    }

    private static final class FakePreferenceHelper implements PreferenceHelper {
        private boolean syncEnabled;
        private boolean backgroundEnabled;
        private boolean firstSyncConfirmed;

        @Override
        public int getFormatCount() {
            return 0;
        }

        @Override
        public String getTypeFaceNoteActivity() {
            return "normal";
        }

        @Override
        public int getSizeTextNoteActivity() {
            return 16;
        }

        @Override
        public String getSortParam() {
            return "";
        }

        @Override
        public String getSortParamTags() {
            return "";
        }

        @Override
        public void setSortParamTags(String paramTags) {}

        @Override
        public void editSizeTextNoteActivity(int value) {}

        @Override
        public com.pasich.mynotes.utils.backup.models.PreferencesBackup getListPreferences() {
            return new com.pasich.mynotes.utils.backup.models.PreferencesBackup();
        }

        @Override
        public void setListPreferences(
                com.pasich.mynotes.utils.backup.models.PreferencesBackup preferences) {}

        @Override
        public String getLastKnownVersion() {
            return "";
        }

        @Override
        public void setLastKnownVersion(String version) {}

        @Override
        public boolean isSyncEnabled() {
            return syncEnabled;
        }

        @Override
        public void setSyncEnabled(boolean enabled) {
            syncEnabled = enabled;
        }

        @Override
        public boolean isBackgroundSyncEnabled() {
            return backgroundEnabled;
        }

        @Override
        public void setBackgroundSyncEnabled(boolean enabled) {
            backgroundEnabled = enabled;
        }

        @Override
        public boolean isFirstSyncConfirmed() {
            return firstSyncConfirmed;
        }

        @Override
        public void setFirstSyncConfirmed(boolean confirmed) {
            firstSyncConfirmed = confirmed;
        }
    }
}
