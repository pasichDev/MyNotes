package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import java.security.SecureRandom;

/**
 * Staged-rollout gate for Google Drive sync.
 *
 * <p>The percentage and the bucket check used to live in {@code SyncCoordinator}, which only the
 * manual "Sync now" button goes through. {@code GoogleDriveSyncWorker} never consulted them, so
 * lowering the percentage to stop a bad release would have left the six-hourly background sync
 * running for every user who had already enabled it — exactly the population a rollback needs to
 * stop. Both paths now share this class.
 */
public final class SyncRollout {

    /**
     * The v2.6.48 sync safety release completed its staged rollout; sync is available to all
     * cohorts. Lower this to pull sync back from part of the population.
     */
    public static final int CURRENT_PERCENT = 100;

    private static final SecureRandom RANDOM = new SecureRandom();

    private SyncRollout() {
        // no instance
    }

    /**
     * Returns this device's stable 1..100 cohort, assigning one on first use.
     *
     * <p>The bucket is drawn once and kept, so a device never moves between cohorts as the
     * percentage changes.
     */
    public static int ensureBucket(@NonNull PreferenceHelper preferences) {
        int bucket = preferences.getSyncRolloutBucket();
        if (bucket < 1 || bucket > 100) {
            bucket = RANDOM.nextInt(100) + 1;
            preferences.setSyncRolloutBucket(bucket);
        }
        return bucket;
    }

    /** True when this device's cohort is inside the current rollout. */
    public static boolean isWithinRollout(@NonNull PreferenceHelper preferences) {
        return ensureBucket(preferences) <= CURRENT_PERCENT;
    }
}
