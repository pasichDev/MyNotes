package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * Decides whether the live preferences differ from the last value sync recorded.
 *
 * <p>SharedPreferences has no mutation hook, so a local settings change is only ever noticed by
 * comparing a digest of the live values against the baseline stored at the last sync. The baseline
 * format changed once: 2.6.49 stored the payload's raw JSON, later releases store its SHA-256. A
 * device upgrading across that change still holds a JSON baseline, and comparing it against a
 * digest can only ever say "changed" — which manufactured a local edit on every upgraded device and
 * let its untouched settings outrank a genuine change made elsewhere.
 *
 * <p>Deliberately free of {@code android.*} so every branch runs under a plain JVM test.
 */
final class PreferencesBaselineDecision {

    enum Action {
        /** Nothing changed since the last sync. */
        UNCHANGED,
        /**
         * The baseline predates the digest format but describes the live values; store the digest.
         */
        MIGRATE_BASELINE,
        /** The user changed a setting since the last sync; the record must be touched. */
        LOCAL_EDIT
    }

    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    private PreferencesBaselineDecision() {}

    /**
     * @param storedBaseline the baseline read from preferences, or {@code null} when none exists.
     * @param liveDigest SHA-256 of the live preferences in the current format.
     * @param legacyFingerprint the live preferences serialized the way 2.6.49 stored its baseline.
     */
    @NonNull
    static Action decide(
            @Nullable String storedBaseline,
            @NonNull String liveDigest,
            @NonNull String legacyFingerprint) {
        if (storedBaseline == null) {
            // No baseline at all means sync has never seen these settings; treating them as a
            // local edit is the conservative reading, because the alternative silently loses a
            // fresh install's configuration to an older version already on Drive.
            return Action.LOCAL_EDIT;
        }
        if (liveDigest.equals(storedBaseline)) {
            return Action.UNCHANGED;
        }
        if (!DIGEST.matcher(storedBaseline).matches() && storedBaseline.equals(legacyFingerprint)) {
            return Action.MIGRATE_BASELINE;
        }
        return Action.LOCAL_EDIT;
    }
}
