package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.data.preferences.PreferenceHelper;
import org.junit.Test;

public class GoogleDriveSyncWorkerTest {

    @Test
    public void backgroundSyncAllowed_requiresExplicitFirstSyncConfirmation() {
        PreferenceHelper preferences = mock(PreferenceHelper.class);
        when(preferences.isSyncEnabled()).thenReturn(true);
        when(preferences.isBackgroundSyncEnabled()).thenReturn(true);
        when(preferences.isFirstSyncConfirmed()).thenReturn(false);

        assertThat(GoogleDriveSyncWorker.isBackgroundSyncAllowed(preferences)).isFalse();
    }

    @Test
    public void backgroundSyncAllowed_acceptsEnabledConfirmedSync() {
        PreferenceHelper preferences = mock(PreferenceHelper.class);
        when(preferences.isSyncEnabled()).thenReturn(true);
        when(preferences.isBackgroundSyncEnabled()).thenReturn(true);
        when(preferences.isFirstSyncConfirmed()).thenReturn(true);
        when(preferences.getSyncRolloutBucket()).thenReturn(SyncRollout.CURRENT_PERCENT);

        assertThat(GoogleDriveSyncWorker.isBackgroundSyncAllowed(preferences)).isTrue();
    }

    @Test
    public void backgroundSyncAllowed_consultsTheRolloutGate() {
        // The gate used to sit only on the manual "Sync now" path. Lowering the percentage to pull
        // a bad release back would then have left this six-hourly job running for exactly the
        // users a rollback needs to stop.
        PreferenceHelper preferences = mock(PreferenceHelper.class);
        when(preferences.isSyncEnabled()).thenReturn(true);
        when(preferences.isBackgroundSyncEnabled()).thenReturn(true);
        when(preferences.isFirstSyncConfirmed()).thenReturn(true);
        when(preferences.getSyncRolloutBucket()).thenReturn(SyncRollout.CURRENT_PERCENT);

        GoogleDriveSyncWorker.isBackgroundSyncAllowed(preferences);

        verify(preferences).getSyncRolloutBucket();
    }
}
