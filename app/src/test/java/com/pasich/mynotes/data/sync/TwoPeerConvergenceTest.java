package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

/**
 * Two peers, one shared bundle history, a conflict settled by hand.
 *
 * <p>Reproduced on a Pixel: peer A deletes a task and syncs, peer B renames it and syncs, B gets
 * the right live-versus-deleted conflict, keeps the live version — and the same conflict returned
 * on every sync forever, because the resolution never reached Drive. The stores here model exactly
 * what RoomSyncStore does with versions, bases and resolutions, against the real codec, merger,
 * service and bundle fold; only the transport is in memory.
 */
public class TwoPeerConvergenceTest {

    private static final String RECORD_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final Instant START = Instant.parse("2026-09-05T12:00:00Z");

    private final MutableClock clock = new MutableClock(START);
    private final InMemoryBundleBackend drive = new InMemoryBundleBackend(clock);

    @Test
    public void aTaskDeletedOnOnePeerAndEditedOnTheOtherSettlesAfterOneResolution() {
        deletedHereEditedThereSettlesAfterOneResolution(SyncRecord.Type.TASK);
    }

    @Test
    public void aNoteDeletedOnOnePeerAndEditedOnTheOtherSettlesAfterOneResolution() {
        deletedHereEditedThereSettlesAfterOneResolution(SyncRecord.Type.NOTE);
    }

    @Test
    public void aTagDeletedOnOnePeerAndEditedOnTheOtherSettlesAfterOneResolution() {
        deletedHereEditedThereSettlesAfterOneResolution(SyncRecord.Type.TAG);
    }

    @Test
    public void aCategoryDeletedOnOnePeerAndEditedOnTheOtherSettlesAfterOneResolution() {
        deletedHereEditedThereSettlesAfterOneResolution(SyncRecord.Type.CATEGORY);
    }

    @Test
    public void aRecordRevivedElsewhereLeavesNoPhantomDeletionOnAPeerThatNeverHeldIt() {
        // B edits, C deletes, B keeps the edit. A — a fresh install that never held the record —
        // received the deletion-versus-edit conflict along the way. Once the edit arrives on A the
        // row is meaningless: offered anyway, pre-selected on the deletion, one tap deleted the
        // record on every device with no conflict raised anywhere.
        SyncRecord.Type type = SyncRecord.Type.TASK;
        Peer b = new Peer("B", clock, drive);
        Peer c = new Peer("C", clock, drive);
        Peer a = new Peer("A", clock, drive);
        b.create(type, RECORD_ID, "TaskX");
        clock.advance();
        assertThat(b.sync().getConflictCount()).isEqualTo(0);
        clock.advance();
        assertThat(c.sync().getConflictCount()).isEqualTo(0);
        clock.advance();
        assertThat(b.sync().getConflictCount()).isEqualTo(0);

        b.edit(type, RECORD_ID, "TaskX-B");
        clock.advance();
        c.delete(type, RECORD_ID);
        clock.advance();
        assertThat(c.sync().getConflictCount()).isEqualTo(0);
        clock.advance();
        assertThat(b.sync().getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(b.store.pendingConflicts()).hasSize(1);
        clock.advance();
        a.sync();

        // B keeps its edit and publishes it.
        clock.advance();
        b.store.resolveAllKeepingLive(clock.instant());
        clock.advance();
        assertThat(b.sync().getConflictCount()).isEqualTo(0);
        clock.advance();
        assertThat(a.sync().getConflictCount()).isEqualTo(0);

        assertThat(a.store.pendingConflicts()).isEmpty();
        assertThat(a.store.titleOf(type, RECORD_ID)).isEqualTo("TaskX-B");
        clock.advance();
        assertThat(c.sync().getConflictCount()).isEqualTo(0);
        assertThat(c.store.titleOf(type, RECORD_ID)).isEqualTo("TaskX-B");
        clock.advance();
        assertThat(a.sync().getConflictCount()).isEqualTo(0);
        clock.advance();
        assertThat(b.sync().getConflictCount()).isEqualTo(0);
        assertThat(b.store.titleOf(type, RECORD_ID)).isEqualTo("TaskX-B");
    }

    @Test
    public void aTaskSyncedBeforeBasesWereRecordedStillSettlesAfterOneResolution() {
        deletedHereEditedThereSettlesAfterOneResolution(SyncRecord.Type.TASK, true);
    }

    private void deletedHereEditedThereSettlesAfterOneResolution(SyncRecord.Type type) {
        deletedHereEditedThereSettlesAfterOneResolution(type, false);
    }

    private void deletedHereEditedThereSettlesAfterOneResolution(
            SyncRecord.Type type, boolean migratedWithoutBases) {
        Peer a = new Peer("A", clock, drive);
        Peer b = new Peer("B", clock, drive);
        a.create(type, RECORD_ID, "TaskX");
        clock.advance();
        assertThat(a.sync().getConflictCount()).isEqualTo(0);
        clock.advance();
        assertThat(b.sync().getConflictCount()).isEqualTo(0);
        clock.advance();
        assertThat(a.sync().getConflictCount()).isEqualTo(0);
        assertThat(b.store.pendingConflicts()).isEmpty();
        assertThat(a.store.pendingConflicts()).isEmpty();
        if (migratedWithoutBases) {
            // Rows migrated from a build that recorded no synced version: null until the next
            // sync fills them in.
            a.store.bases.clear();
            b.store.bases.clear();
        }

        // A deletes and syncs; B edits and syncs. A genuine conflict, seen once on B.
        a.delete(type, RECORD_ID);
        clock.advance();
        // A record whose synced version is not yet known merges as before: the deletion is
        // reported against the version it replaced, and that version travels as an alternative.
        assertThat(a.sync().getConflictCount()).isEqualTo(migratedWithoutBases ? 1 : 0);
        clock.advance();
        b.edit(type, RECORD_ID, "TaskX-B");
        clock.advance();
        SyncState firstOnB = b.sync();
        assertThat(firstOnB.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(b.store.pendingConflicts()).hasSize(migratedWithoutBases ? 2 : 1);

        // B keeps the live version, in one sitting — the dialog offers every open conflict — and
        // syncs: the choice reaches the history, and nothing is offered again — on B, on B once
        // more, or on A, which gets the record back.
        clock.advance();
        b.store.resolveAllKeepingLive(clock.instant());
        clock.advance();
        SyncState publishing = b.sync();
        assertThat(publishing.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(publishing.getErrorMessage()).isNull();
        assertThat(b.store.pendingConflicts()).isEmpty();
        clock.advance();
        SyncState secondOnB = b.sync();
        assertThat(secondOnB.getConflictCount()).isEqualTo(0);
        assertThat(b.store.pendingConflicts()).isEmpty();
        clock.advance();
        SyncState onA = a.sync();
        assertThat(onA.getStatus()).isEqualTo(SyncState.Status.SUCCESS);
        assertThat(onA.getConflictCount()).isEqualTo(0);
        assertThat(a.store.pendingConflicts()).isEmpty();
        assertThat(a.store.titleOf(type, RECORD_ID)).isEqualTo("TaskX-B");
        assertThat(b.store.titleOf(type, RECORD_ID)).isEqualTo("TaskX-B");
        clock.advance();
        assertThat(a.sync().getConflictCount()).isEqualTo(0);
        clock.advance();
        assertThat(b.sync().getConflictCount()).isEqualTo(0);
    }

    // ------------------------------------------------------------------ the two peers

    private static final class Peer {
        final String name;
        final PeerStore store;
        private final Clock clock;
        private final SyncBackend backend;

        Peer(String name, Clock clock, SyncBackend backend) {
            this.name = name;
            this.clock = clock;
            this.backend = backend;
            this.store = new PeerStore(clock);
        }

        SyncState sync() {
            return new SyncService(store, new SyncMerger(), clock).sync(backend);
        }

        void create(SyncRecord.Type type, String id, String title) {
            store.put(SyncRecord.live(type, id, clock.instant(), payload(title)), null);
        }

        void edit(SyncRecord.Type type, String id, String title) {
            store.touchLive(type, id, payload(title));
        }

        void delete(SyncRecord.Type type, String id) {
            store.tombstone(type, id);
        }

        private static JsonObject payload(String title) {
            JsonObject payload = new JsonObject();
            payload.addProperty("title", title);
            return payload;
        }
    }

    /** RoomSyncStore's versioning, bases, conflict rows and resolutions, without Room. */
    static final class PeerStore implements SyncStore {
        private final Clock clock;
        private final Map<String, SyncRecord> records = new LinkedHashMap<>();
        private final Map<String, String> bases = new LinkedHashMap<>();
        private final List<ConflictRow> conflicts = new ArrayList<>();
        private SyncState state = SyncState.idle();

        PeerStore(Clock clock) {
            this.clock = clock;
        }

        static final class ConflictRow {
            final SyncMergeResult.Conflict conflict;
            boolean resolved;

            ConflictRow(SyncMergeResult.Conflict conflict) {
                this.conflict = conflict;
            }
        }

        void put(SyncRecord record, String base) {
            records.put(key(record), record);
            if (base != null) bases.put(key(record), base);
        }

        void touchLive(SyncRecord.Type type, String id, JsonObject payload) {
            SyncRecord current = records.get(type + ":" + id);
            records.put(type + ":" + id, SyncRecord.live(type, id, next(current), payload));
        }

        void tombstone(SyncRecord.Type type, String id) {
            SyncRecord current = records.get(type + ":" + id);
            Instant at = next(current);
            records.put(type + ":" + id, SyncRecord.tombstone(type, id, at, at));
        }

        private Instant next(SyncRecord current) {
            Instant now = clock.instant();
            return current == null || now.isAfter(current.getUpdatedAt())
                    ? now
                    : current.getUpdatedAt().plusMillis(1);
        }

        String titleOf(SyncRecord.Type type, String id) {
            SyncRecord record = records.get(type + ":" + id);
            return record == null || record.isTombstone()
                    ? null
                    : record.getPayload().get("title").getAsString();
        }

        List<ConflictRow> pendingConflicts() {
            List<ConflictRow> pending = new ArrayList<>();
            for (ConflictRow row : conflicts) {
                if (!row.resolved) pending.add(row);
            }
            return pending;
        }

        /** The dialog loop: every open conflict, one after another, keeping the live version. */
        void resolveAllKeepingLive(Instant resolvedAt) {
            for (ConflictRow row : new ArrayList<>(pendingConflicts())) {
                resolveKeepingLive(row, resolvedAt);
            }
        }

        /**
         * What resolveConflict does: drop a row whose record's content has moved on, otherwise
         * re-time the chosen version and mark the row settled.
         */
        void resolveKeepingLive(ConflictRow row, Instant resolvedAt) {
            SyncRecord chosen =
                    row.conflict.getWinner().isTombstone()
                            ? row.conflict.getLoser()
                            : row.conflict.getWinner();
            String key = key(chosen);
            SyncRecord current = records.get(key);
            if (current != null
                    && current.getUpdatedAt()
                            .isAfter(
                                    row.conflict
                                                    .getWinner()
                                                    .getUpdatedAt()
                                                    .isAfter(row.conflict.getLoser().getUpdatedAt())
                                            ? row.conflict.getWinner().getUpdatedAt()
                                            : row.conflict.getLoser().getUpdatedAt())
                    && !contentDigest(current).equals(contentDigest(row.conflict.getWinner()))) {
                conflicts.remove(row);
                return;
            }
            Instant updatedAt =
                    current != null && !resolvedAt.isAfter(current.getUpdatedAt())
                            ? current.getUpdatedAt().plusMillis(1)
                            : resolvedAt;
            records.put(
                    key,
                    SyncRecord.live(
                            chosen.getType(), chosen.getId(), updatedAt, chosen.getPayload()));
            row.resolved = true;
        }

        @Override
        public SyncSnapshot readSnapshot() {
            List<SyncRecord> withBases = new ArrayList<>();
            for (Map.Entry<String, SyncRecord> entry : records.entrySet()) {
                withBases.add(entry.getValue().withBaseVersion(bases.get(entry.getKey())));
            }
            return new SyncSnapshot(withBases);
        }

        @Override
        public void applySnapshot(SyncSnapshot snapshot, List<SyncMergeResult.Conflict> incoming) {
            Set<String> skipped = new LinkedHashSet<>();
            for (SyncRecord record : snapshot.getRecords()) {
                String key = key(record);
                SyncRecord current = records.get(key);
                if (current != null && current.getUpdatedAt().isAfter(record.getUpdatedAt())) {
                    skipped.add(key);
                    continue;
                }
                records.put(key, record.withBaseVersion(null));
                bases.put(key, record.getCanonicalPayloadHash());
                conflicts.removeIf(
                        row ->
                                !row.resolved
                                        && key.equals(
                                                row.conflict.getType() + ":" + row.conflict.getId())
                                        && !row.conflict
                                                .getWinnerVersionId()
                                                .equals(record.getCanonicalPayloadHash()));
            }
            for (SyncMergeResult.Conflict conflict : incoming) {
                String key = conflict.getType() + ":" + conflict.getId();
                if (skipped.contains(key)) continue;
                conflicts.removeIf(
                        row ->
                                !row.resolved
                                        && key.equals(
                                                row.conflict.getType() + ":" + row.conflict.getId())
                                        && !row.conflict
                                                .getWinnerVersionId()
                                                .equals(conflict.getWinnerVersionId()));
                boolean duplicate = false;
                for (ConflictRow row : conflicts) {
                    if (row.conflict.getWinnerVersionId().equals(conflict.getWinnerVersionId())
                            && row.conflict
                                    .getLoserVersionId()
                                    .equals(conflict.getLoserVersionId())) {
                        duplicate = true;
                    }
                }
                if (!duplicate) conflicts.add(new ConflictRow(conflict));
            }
        }

        @Override
        public Set<String> getResolvedAlternativeIds() {
            Set<String> settled = new LinkedHashSet<>();
            for (ConflictRow row : conflicts) {
                if (row.resolved) {
                    settled.add(row.conflict.getWinnerVersionId());
                    settled.add(row.conflict.getLoserVersionId());
                }
            }
            return settled;
        }

        @Override
        public Collection<String> getAttachmentHashes(SyncSnapshot snapshot) {
            return Collections.emptyList();
        }

        @Override
        public boolean hasAttachment(String sha256) {
            return false;
        }

        @Override
        public InputStream readAttachment(String sha256) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void writeAttachment(String sha256, long sizeBytes, InputStream content) {}

        @Override
        public SyncState readState() {
            return state;
        }

        @Override
        public void writeState(SyncState state) {
            this.state = state;
        }

        private static String key(SyncRecord record) {
            return record.getType() + ":" + record.getId();
        }

        private static String contentDigest(SyncRecord record) {
            return record.isTombstone()
                    ? "tombstone"
                    : SyncRecord.live(
                                    record.getType(),
                                    record.getId(),
                                    Instant.EPOCH,
                                    record.getPayload())
                            .getCanonicalPayloadHash();
        }
    }

    /** Drive without the network: the same codec and the same fold over the same history. */
    static final class InMemoryBundleBackend implements SyncBackend {
        private final Clock clock;
        private final SyncBundleCodec codec = new SyncBundleCodec();
        private final Map<String, byte[]> bundles = new LinkedHashMap<>();
        private String lastReadToken = "";

        InMemoryBundleBackend(Clock clock) {
            this.clock = clock;
        }

        @Override
        public String getIdentifier() {
            return "memory";
        }

        @Override
        public RemoteSnapshot readSnapshotResult() throws IOException {
            Map<String, SyncBundleCodec.DecodedBundle> decoded = new LinkedHashMap<>();
            for (byte[] bytes : bundles.values()) {
                SyncBundleCodec.DecodedBundle bundle =
                        codec.decode(new ByteArrayInputStream(bytes));
                decoded.put(bundle.getBundleId(), bundle);
            }
            BundleHistory.Fold fold = BundleHistory.fold(decoded, new SyncMerger());
            lastReadToken = UUID.randomUUID().toString();
            return new RemoteSnapshot(
                    fold.merged,
                    fold.conflicts,
                    fold.frontier,
                    fold.alternatives,
                    fold.resolvedAlternativeIds,
                    lastReadToken);
        }

        @Override
        public void publish(SyncPublication publication) throws IOException {
            if (!publication.getReadContext().getReadToken().equals(lastReadToken)) {
                throw new IOException("stale read context");
            }
            byte[] bytes =
                    codec.encode(
                            publication.getSnapshot(),
                            clock.instant(),
                            publication.getReadContext().getFrontierBundleIds(),
                            publication.getUnresolvedAlternatives(),
                            publication.getResolvedAlternativeIds());
            bundles.put(codec.decode(new ByteArrayInputStream(bytes)).getBundleId(), bytes);
        }

        @Override
        public boolean hasAttachment(String sha256) {
            return false;
        }

        @Override
        public InputStream readAttachment(String sha256) {
            return null;
        }

        @Override
        public void writeAttachment(String sha256, long sizeBytes, InputStream content) {}
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            now = start;
        }

        void advance() {
            now = now.plus(Duration.ofMinutes(1));
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
