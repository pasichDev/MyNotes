package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.Arrays;
import org.junit.Test;

public class SyncMergerTest {

    private static final String NOTE_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TASK_ID = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
    private static final Instant TEN = Instant.parse("2026-08-31T12:00:10Z");
    private static final Instant TWENTY = Instant.parse("2026-08-31T12:00:20Z");

    private final SyncMerger merger = new SyncMerger();

    @Test
    public void merge_identicalVersionIsNoOp() {
        SyncRecord note = note(NOTE_ID, TEN, "Milk");

        SyncMergeResult result = merger.merge(snapshot(note), snapshot(note));

        assertThat(result.getMergedSnapshot().getRecords()).containsExactly(note);
        assertThat(result.hasConflicts()).isFalse();
        assertThat(result.getDiscardedRecords()).isEmpty();
    }

    @Test
    public void merge_keepsLocalOnlyAndRemoteOnlyRecords() {
        SyncRecord localNote = note(NOTE_ID, TEN, "Local");
        SyncRecord remoteTask = task(TASK_ID, TEN, "Remote");

        SyncMergeResult result = merger.merge(snapshot(localNote), snapshot(remoteTask));

        assertThat(result.getMergedSnapshot().getRecords()).containsExactly(localNote, remoteTask);
        assertThat(result.getConflicts()).isEmpty();
    }

    @Test
    public void merge_newerRemoteRecordWinsAndReportsDiscardedLocalVersion() {
        SyncRecord local = note(NOTE_ID, TEN, "Old local text");
        SyncRecord remote = note(NOTE_ID, TWENTY, "New remote text");

        SyncMergeResult result = merger.merge(snapshot(local), snapshot(remote));

        assertThat(result.getMergedSnapshot().getRecords()).containsExactly(remote);
        assertThat(result.getConflicts()).hasSize(1);
        SyncMergeResult.Conflict conflict = result.getConflicts().get(0);
        assertThat(conflict.getWinner()).isSameInstanceAs(remote);
        assertThat(conflict.getLoser()).isSameInstanceAs(local);
        assertThat(conflict.getWinnerSource()).isEqualTo(SyncMergeResult.Source.REMOTE);
        assertThat(conflict.isWinnerTombstone()).isFalse();
    }

    @Test
    public void merge_newerTombstoneWinsOverOlderLiveRecord() {
        SyncRecord live = note(NOTE_ID, TEN, "Should be deleted");
        SyncRecord tombstone = SyncRecord.tombstone(SyncRecord.Type.NOTE, NOTE_ID, TWENTY, TWENTY);

        SyncMergeResult result = merger.merge(snapshot(live), snapshot(tombstone));

        assertThat(result.getMergedSnapshot().getRecords()).containsExactly(tombstone);
        assertThat(result.getConflicts()).hasSize(1);
        assertThat(result.getConflicts().get(0).isWinnerTombstone()).isTrue();
        assertThat(result.getConflicts().get(0).isLoserTombstone()).isFalse();
    }

    @Test
    public void merge_newerLiveRecordDoesNotGetDeletedByOlderTombstone() {
        SyncRecord live = note(NOTE_ID, TWENTY, "Restored after deletion");
        SyncRecord tombstone = SyncRecord.tombstone(SyncRecord.Type.NOTE, NOTE_ID, TEN, TEN);

        SyncMergeResult result = merger.merge(snapshot(live), snapshot(tombstone));

        assertThat(result.getMergedSnapshot().getRecords()).containsExactly(live);
        assertThat(result.getConflicts()).hasSize(1);
        assertThat(result.getConflicts().get(0).isLoserTombstone()).isTrue();
    }

    @Test
    public void merge_equalTimestampsUsesPayloadHashAndIsOrderIndependent() {
        SyncRecord local = note(NOTE_ID, TEN, "Local conflict");
        SyncRecord remote = note(NOTE_ID, TEN, "Remote conflict");

        SyncMergeResult forward = merger.merge(snapshot(local), snapshot(remote));
        SyncMergeResult reverse = merger.merge(snapshot(remote), snapshot(local));

        SyncRecord expected =
                local.getCanonicalPayloadHash().compareTo(remote.getCanonicalPayloadHash()) < 0
                        ? local
                        : remote;
        assertThat(forward.getMergedSnapshot().getRecords()).containsExactly(expected);
        assertThat(reverse.getMergedSnapshot().getRecords()).containsExactly(expected);
        assertThat(forward.getConflicts()).hasSize(1);
        assertThat(reverse.getConflicts()).hasSize(1);
    }

    @Test
    public void record_hashIsIndependentOfJsonObjectFieldOrder() {
        JsonObject firstPayload = new JsonObject();
        firstPayload.addProperty("title", "Shopping");
        firstPayload.addProperty("value", "Milk");
        JsonObject secondPayload = new JsonObject();
        secondPayload.addProperty("value", "Milk");
        secondPayload.addProperty("title", "Shopping");

        SyncRecord first = SyncRecord.live(SyncRecord.Type.NOTE, NOTE_ID, TEN, firstPayload);
        SyncRecord second = SyncRecord.live(SyncRecord.Type.NOTE, NOTE_ID, TEN, secondPayload);
        SyncMergeResult result = merger.merge(snapshot(first), snapshot(second));

        assertThat(first.getCanonicalPayloadHash()).isEqualTo(second.getCanonicalPayloadHash());
        assertThat(result.hasConflicts()).isFalse();
    }

    @Test
    public void snapshot_rejectsDuplicateLiveAndTombstoneVersions() {
        SyncRecord live = note(NOTE_ID, TEN, "Live");
        SyncRecord tombstone = SyncRecord.tombstone(SyncRecord.Type.NOTE, NOTE_ID, TWENTY, TWENTY);

        try {
            new SyncSnapshot(Arrays.asList(live, tombstone));
        } catch (IllegalArgumentException exception) {
            assertThat(exception).hasMessageThat().contains("only one version");
            return;
        }
        throw new AssertionError("Expected duplicate sync record identity to be rejected");
    }

    @Test
    public void record_rejectsNonCanonicalUuidAndInvalidDeletionTimestamp() {
        JsonObject payload = new JsonObject();
        payload.addProperty("title", "Bad record");

        try {
            SyncRecord.live(
                    SyncRecord.Type.NOTE, "550E8400-E29B-41D4-A716-446655440000", TEN, payload);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }

        try {
            SyncRecord.tombstone(SyncRecord.Type.NOTE, NOTE_ID, TWENTY, TEN);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected deletedAt before updatedAt to be rejected");
    }

    @Test
    public void record_defensivelyCopiesPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("title", "Original");
        SyncRecord record = SyncRecord.live(SyncRecord.Type.NOTE, NOTE_ID, TEN, payload);
        payload.addProperty("title", "Mutated outside record");

        JsonObject returnedPayload = record.getPayload();
        returnedPayload.addProperty("title", "Mutated returned copy");

        assertThat(record.getPayload().get("title").getAsString()).isEqualTo("Original");
    }

    private static SyncSnapshot snapshot(SyncRecord... records) {
        return new SyncSnapshot(Arrays.asList(records));
    }

    private static SyncRecord note(String id, Instant updatedAt, String value) {
        JsonObject payload = new JsonObject();
        payload.addProperty("title", "Shopping");
        payload.addProperty("value", value);
        return SyncRecord.live(SyncRecord.Type.NOTE, id, updatedAt, payload);
    }

    private static SyncRecord task(String id, Instant updatedAt, String title) {
        JsonObject payload = new JsonObject();
        payload.addProperty("title", title);
        payload.addProperty("isDone", false);
        return SyncRecord.live(SyncRecord.Type.TASK, id, updatedAt, payload);
    }
}
