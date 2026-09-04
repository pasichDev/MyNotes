package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/**
 * Whether the live settings count as a local edit.
 *
 * <p>The baseline changed format once — 2.6.49 stored the payload JSON, later releases its digest —
 * and every device upgrading across that boundary claimed a local edit it never made, letting its
 * untouched settings outrank a genuine change from another device.
 */
public class PreferencesBaselineDecisionTest {

    private static final String LEGACY_JSON = "{\"a\":1,\"b\":14,\"c\":11,\"g\":true}";
    private static final String DIGEST = Sha256.of(LEGACY_JSON);

    @Test
    public void unchangedSettingsAreNotAnEdit() {
        assertThat(PreferencesBaselineDecision.decide(DIGEST, DIGEST, LEGACY_JSON))
                .isEqualTo(PreferencesBaselineDecision.Action.UNCHANGED);
    }

    @Test
    public void aLegacyBaselineDescribingTheLiveValuesIsMigratedNotTreatedAsAnEdit() {
        assertThat(PreferencesBaselineDecision.decide(LEGACY_JSON, DIGEST, LEGACY_JSON))
                .isEqualTo(PreferencesBaselineDecision.Action.MIGRATE_BASELINE);
    }

    @Test
    public void aLegacyBaselineForOtherValuesIsAnEdit() {
        assertThat(
                        PreferencesBaselineDecision.decide(
                                "{\"a\":1,\"b\":14,\"c\":2,\"g\":true}", DIGEST, LEGACY_JSON))
                .isEqualTo(PreferencesBaselineDecision.Action.LOCAL_EDIT);
    }

    @Test
    public void aDifferentDigestIsAnEdit() {
        assertThat(PreferencesBaselineDecision.decide(Sha256.of("other"), DIGEST, LEGACY_JSON))
                .isEqualTo(PreferencesBaselineDecision.Action.LOCAL_EDIT);
    }

    @Test
    public void noBaselineAtAllIsAnEdit() {
        // Sync has never seen these settings; publishing them is the conservative reading.
        assertThat(PreferencesBaselineDecision.decide(null, DIGEST, LEGACY_JSON))
                .isEqualTo(PreferencesBaselineDecision.Action.LOCAL_EDIT);
    }
}
