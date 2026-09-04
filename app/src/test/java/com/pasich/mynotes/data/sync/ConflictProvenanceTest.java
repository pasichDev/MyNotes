package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Where each side of a conflict actually came from.
 *
 * <p>The old model recorded one {@code Source} for the winner and inferred the loser's from it.
 * That is wrong for a conflict between two Drive bundle heads, where neither side is local: the
 * merge accumulator was reported as "this device", so the UI named a version the device had never
 * held and {@code KEEP_LOCAL} applied it.
 */
public class ConflictProvenanceTest {

    private static final String NOTE = "550e8400-e29b-41d4-a716-446655440000";
    private static final Instant T10 = Instant.parse("2026-08-31T12:00:10Z");
    private static final Instant T20 = Instant.parse("2026-08-31T12:00:20Z");

    @Test
    public void localVersusDrive_namesOneSideLocalAndTheOtherRemote() {
        SyncMergeResult result =
                new SyncMerger()
                        .merge(
                                snapshot(note(T20, "from the phone")),
                                snapshot(note(T10, "from Drive")));

        SyncMergeResult.Conflict conflict = only(result);
        assertThat(conflict.getWinnerSource()).isEqualTo(SyncMergeResult.Source.LOCAL);
        assertThat(conflict.getLoserSource()).isEqualTo(SyncMergeResult.Source.REMOTE);
    }

    @Test
    public void driveWinningOverLocal_stillNamesEachSideCorrectly() {
        SyncMergeResult result =
                new SyncMerger()
                        .merge(
                                snapshot(note(T10, "from the phone")),
                                snapshot(note(T20, "from Drive")));

        SyncMergeResult.Conflict conflict = only(result);
        assertThat(conflict.getWinnerSource()).isEqualTo(SyncMergeResult.Source.REMOTE);
        assertThat(conflict.getLoserSource()).isEqualTo(SyncMergeResult.Source.LOCAL);
    }

    @Test
    public void remoteVersusRemote_neverClaimsAVersionCameFromThisDevice() {
        SyncMergeResult result =
                new SyncMerger()
                        .merge(
                                snapshot(note(T20, "bundle A")),
                                snapshot(note(T10, "bundle B")),
                                SyncMergeResult.Source.REMOTE,
                                SyncMergeResult.Source.REMOTE);

        SyncMergeResult.Conflict conflict = only(result);
        assertThat(conflict.getWinnerSource()).isEqualTo(SyncMergeResult.Source.REMOTE);
        assertThat(conflict.getLoserSource()).isEqualTo(SyncMergeResult.Source.REMOTE);
    }

    @Test
    public void aThreeWayMergeReportsEachPairWithItsOwnOrigins() {
        // Two Drive heads folded together, then merged against local state.
        SyncMerger merger = new SyncMerger();
        SyncMergeResult remoteFold =
                merger.merge(
                        snapshot(note(T20, "bundle A")),
                        snapshot(note(T10, "bundle B")),
                        SyncMergeResult.Source.REMOTE,
                        SyncMergeResult.Source.REMOTE);
        SyncMergeResult againstLocal =
                merger.merge(snapshot(note(T10, "local edit")), remoteFold.getMergedSnapshot());

        assertThat(only(remoteFold).getWinnerSource()).isEqualTo(SyncMergeResult.Source.REMOTE);
        assertThat(only(remoteFold).getLoserSource()).isEqualTo(SyncMergeResult.Source.REMOTE);
        assertThat(only(againstLocal).getWinnerSource()).isEqualTo(SyncMergeResult.Source.REMOTE);
        assertThat(only(againstLocal).getLoserSource()).isEqualTo(SyncMergeResult.Source.LOCAL);
    }

    @Test
    public void twoConflictsForOneRecord_carryDistinctVersionIdentities() {
        SyncMerger merger = new SyncMerger();
        SyncMergeResult remoteFold =
                merger.merge(
                        snapshot(note(T20, "bundle A")),
                        snapshot(note(T10, "bundle B")),
                        SyncMergeResult.Source.REMOTE,
                        SyncMergeResult.Source.REMOTE);
        SyncMergeResult againstLocal =
                merger.merge(snapshot(note(T10, "local edit")), remoteFold.getMergedSnapshot());

        List<SyncMergeResult.Conflict> both = Arrays.asList(only(remoteFold), only(againstLocal));

        assertThat(both.get(0).getId()).isEqualTo(both.get(1).getId());
        // Same record, genuinely different version pairs; identities must not collide.
        assertThat(both.get(0).getLoserVersionId()).isNotEqualTo(both.get(1).getLoserVersionId());
        assertThat(both.get(0).getWinnerVersionId()).isEqualTo(both.get(1).getWinnerVersionId());
    }

    @Test
    public void versionIdentityIsDeterministicAcrossDevices() {
        SyncRecord one = note(T10, "same content");
        SyncRecord other = note(T10, "same content");

        assertThat(one.getCanonicalPayloadHash()).isEqualTo(other.getCanonicalPayloadHash());
        assertThat(one.getCanonicalPayloadHash())
                .isNotEqualTo(note(T10, "different").getCanonicalPayloadHash());
    }

    @Test
    public void resolutionValuesAddressVersionsRatherThanEndpoints() {
        assertThat(SyncResolution.KEEP_WINNER.isVersionAddressed()).isTrue();
        assertThat(SyncResolution.KEEP_ALTERNATIVE.isVersionAddressed()).isTrue();
        assertThat(SyncResolution.KEEP_LOCAL.isVersionAddressed()).isFalse();
        assertThat(SyncResolution.KEEP_DRIVE.isVersionAddressed()).isFalse();
        // Historical rows still render.
        assertThat(SyncResolution.fromStoredValue("KEEP_LOCAL"))
                .isEqualTo(SyncResolution.KEEP_LOCAL);
        assertThat(SyncResolution.fromStoredValue("NONSENSE")).isEqualTo(SyncResolution.PENDING);
    }

    private static SyncMergeResult.Conflict only(SyncMergeResult result) {
        assertThat(result.getConflicts()).hasSize(1);
        return result.getConflicts().get(0);
    }

    private static SyncSnapshot snapshot(SyncRecord record) {
        return new SyncSnapshot(java.util.Collections.singletonList(record));
    }

    private static SyncRecord note(Instant updatedAt, String value) {
        JsonObject payload = new JsonObject();
        payload.addProperty("title", "Shopping");
        payload.addProperty("value", value);
        return SyncRecord.live(SyncRecord.Type.NOTE, NOTE, updatedAt, payload);
    }
}
