package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
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

        assertThat(GoogleDriveSyncWorker.isBackgroundSyncAllowed(preferences)).isTrue();
    }
}
