package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.data.preferences.PreferenceHelper;
import org.junit.Test;

public class SyncRolloutTest {

    @Test
    public void ensureBucket_keepsAnAlreadyAssignedCohort() {
        PreferenceHelper preferences = mock(PreferenceHelper.class);
        when(preferences.getSyncRolloutBucket()).thenReturn(37);

        assertThat(SyncRollout.ensureBucket(preferences)).isEqualTo(37);
    }

    @Test
    public void ensureBucket_assignsAValidCohortWhenStoredValueIsOutOfRange() {
        for (int stored : new int[] {-1, 0, 101}) {
            PreferenceHelper preferences = mock(PreferenceHelper.class);
            when(preferences.getSyncRolloutBucket()).thenReturn(stored);

            int bucket = SyncRollout.ensureBucket(preferences);

            assertThat(bucket).isAtLeast(1);
            assertThat(bucket).isAtMost(100);
        }
    }

    @Test
    public void isWithinRollout_followsTheCurrentPercentage() {
        PreferenceHelper inside = mock(PreferenceHelper.class);
        when(inside.getSyncRolloutBucket()).thenReturn(SyncRollout.CURRENT_PERCENT);
        assertThat(SyncRollout.isWithinRollout(inside)).isTrue();

        // Only meaningful once the percentage is dialled back below 100 for a rollback.
        if (SyncRollout.CURRENT_PERCENT < 100) {
            PreferenceHelper outside = mock(PreferenceHelper.class);
            when(outside.getSyncRolloutBucket()).thenReturn(SyncRollout.CURRENT_PERCENT + 1);
            assertThat(SyncRollout.isWithinRollout(outside)).isFalse();
        }
    }
}
