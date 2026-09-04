package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import com.pasich.mynotes.data.sync.PendingPreferencesDecision.Action;
import org.junit.Test;

/**
 * Every crash window around the preferences journal.
 *
 * <p>The journal bridges a committed Room transaction to a SharedPreferences write that Room cannot
 * roll back, so what happens after an interruption is decided here rather than by replaying
 * blindly.
 */
public class PendingPreferencesDecisionTest {

    private static final String BASELINE = "baseline-digest";
    private static final String TARGET = "target-digest";

    @Test
    public void noJournal_doesNothing() {
        assertThat(decide(false, true, TARGET, BASELINE, BASELINE)).isEqualTo(Action.NOTHING);
    }

    @Test
    public void crashBeforeThePreferencesWrite_replaysTheJournal() {
        // Room committed, the adapter never ran: the live values are still the baseline.
        assertThat(decide(true, true, TARGET, BASELINE, BASELINE)).isEqualTo(Action.REPLAY);
    }

    @Test
    public void crashAfterCommitButBeforeTheJournalClear_justClearsTheJournal() {
        assertThat(decide(true, true, TARGET, BASELINE, TARGET))
                .isEqualTo(Action.CLEAR_ALREADY_APPLIED);
    }

    @Test
    public void aLocalEditAfterTheJournalWasWritten_discardsTheStaleJournal() {
        // The user changed these settings themselves; a stale remote payload must not win.
        assertThat(decide(true, true, TARGET, BASELINE, "edited-by-the-user"))
                .isEqualTo(Action.DISCARD_STALE);
    }

    @Test
    public void anUnreadablePayload_isQuarantinedRatherThanFatal() {
        assertThat(decide(true, false, TARGET, BASELINE, BASELINE)).isEqualTo(Action.QUARANTINE);
        // Quarantine wins even when the digests would otherwise say "replay".
        assertThat(decide(true, false, "", "", BASELINE)).isEqualTo(Action.QUARANTINE);
    }

    @Test
    public void aJournalWrittenBeforeIdentityExisted_isStillReplayed() {
        assertThat(decide(true, true, "", "", "anything")).isEqualTo(Action.REPLAY);
    }

    @Test
    public void anAlreadyAppliedJournalWins_overAMissingBaseline() {
        assertThat(decide(true, true, TARGET, "", TARGET)).isEqualTo(Action.CLEAR_ALREADY_APPLIED);
    }

    @Test
    public void retryingAFailedCommit_replaysWhileTheBaselineStillHolds() {
        // First attempt: adapter reported failure, journal intact, values untouched.
        assertThat(decide(true, true, TARGET, BASELINE, BASELINE)).isEqualTo(Action.REPLAY);
        // Second attempt succeeded, so the following pass only has to clear the row.
        assertThat(decide(true, true, TARGET, BASELINE, TARGET))
                .isEqualTo(Action.CLEAR_ALREADY_APPLIED);
    }

    private static Action decide(
            boolean rowPresent,
            boolean payloadReadable,
            String targetHash,
            String baselineHash,
            String liveHash) {
        return PendingPreferencesDecision.decide(
                rowPresent, payloadReadable, targetHash, baselineHash, liveHash);
    }
}
