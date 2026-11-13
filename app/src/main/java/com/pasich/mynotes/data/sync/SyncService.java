package com.pasich.mynotes.data.sync;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Coordinates one manual sync without depending on a cloud provider or Room.
 *
 * <p>The order is intentional: merge first, make all immutable attachment blobs available at both
 * endpoints, publish the remote snapshot, then apply the local snapshot transaction. Therefore a
 * failed upload can leave harmless orphaned blobs, but it can never publish records that reference
 * unavailable attachments or partially apply remote records locally.
 */
public final class SyncService {

    private static final String TAG = "SyncService";
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final long MAX_TOLERATED_CLOCK_SKEW_MILLIS = 24L * 60L * 60L * 1000L;

    /** Well under the schema record limit, so a bundle can always still be published. */
    static final int MAX_PUBLISHED_SETTLED_IDS = 2_000;

    /** How long a deletion keeps travelling in every bundle; see {@link #forPublication}. */
    static final long TOMBSTONE_PUBLICATION_HORIZON_MILLIS = 90L * 24L * 60L * 60L * 1000L;

    private final SyncStore store;
    private final SyncMerger merger;
    private final Clock clock;

    public SyncService(@NonNull SyncStore store) {
        this(store, new SyncMerger(), Clock.systemUTC());
    }

    public SyncService(@NonNull SyncStore store, @NonNull SyncMerger merger, @NonNull Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.merger = Objects.requireNonNull(merger, "merger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Serializes every sync attempt in the process.
     *
     * <p>This method used to rely on {@code synchronized}, but a fresh {@code SyncService} is
     * constructed for each attempt — once by the Backup screen and once by {@code
     * GoogleDriveSyncWorker} — so the monitor was per-instance and guarded nothing. A manual sync
     * and the six-hourly worker could interleave their Room writes and both publish a bundle.
     */
    private static final ReentrantLock SYNC_LOCK = new ReentrantLock();

    private static final long LOCK_WAIT_SECONDS = 5L;

    /**
     * Runs {@code action} while no sync is in flight, waiting for a running one to finish first.
     *
     * <p>For work that tears down what a sync writes — the disconnect wipe of state, conflicts and
     * cached blobs. Done concurrently, that wipe raced the six-hourly worker: the worker's cache
     * directory vanished under it and its final state and conflict rows landed after the wipe.
     */
    public static void runWhileNoSyncRuns(@NonNull Runnable action) {
        SYNC_LOCK.lock();
        try {
            action.run();
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    /** Runs one serialized synchronization attempt and returns its durable final state. */
    @NonNull
    public SyncState sync(@NonNull SyncBackend backend) {
        return sync(backend, () -> true);
    }

    /**
     * Runs one serialized synchronization attempt, unless {@code stillEnabled} says otherwise once
     * the lock is held.
     *
     * <p>Disconnect turns sync off and then wipes the account's state under the lock. A worker that
     * had already passed its own checks and was waiting for a token could still take the lock after
     * that wipe and write the old account's state and conflicts back. The predicate is evaluated
     * under the lock, after the wipe has either finished or not yet begun, so a disabled sync never
     * gets to write anything.
     */
    @NonNull
    public SyncState sync(
            @NonNull SyncBackend backend,
            @NonNull java.util.function.BooleanSupplier stillEnabled) {
        boolean acquired = false;
        try {
            acquired = SYNC_LOCK.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (!acquired) {
            // Deliberately not persisted: the sync that holds the lock owns the stored state.
            // The wording keeps GoogleDriveSyncWorker.isRetryable() treating this as retryable.
            return SyncState.error(
                    "google-drive",
                    safeReadState().getLastSuccessfulSyncAt(),
                    "Another sync is already running; this attempt was temporarily skipped");
        }
        try {
            if (!stillEnabled.getAsBoolean()) {
                // Deliberately not persisted: there is no account left to record it for.
                return SyncState.error(
                        "google-drive",
                        safeReadState().getLastSuccessfulSyncAt(),
                        "Sync was turned off before this attempt could start");
            }
            return syncExclusively(backend);
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    @NonNull
    private SyncState syncExclusively(@NonNull SyncBackend backend) {
        SyncState previousState = safeReadState();
        String backendIdentifier = "unknown";

        try {
            Objects.requireNonNull(backend, "backend");
            backendIdentifier = requireBackendIdentifier(backend.getIdentifier());
            Instant startedAt = clock.instant();
            persistState(
                    SyncState.syncing(
                            backendIdentifier, startedAt, previousState.getLastSuccessfulSyncAt()));
            SnapshotBuildResult localBuild =
                    Objects.requireNonNull(store.buildSnapshot(), "local snapshot build");
            // Do this before reading Drive or transferring blobs. Publishing a snapshot that
            // merely skipped an unresolved local attachment turns a local storage fault into
            // permanent remote data loss on the next successful sync from another device.
            SyncSnapshot local = localBuild.requireSnapshot();
            RemoteSnapshot remoteResult =
                    Objects.requireNonNull(backend.readSnapshotResult(), "remote snapshot");
            SyncSnapshot remote = remoteResult.getSnapshot();
            warnAboutClockSkew(remote);
            SyncMergeResult mergeResult = merger.merge(local, remote);
            SyncSnapshot merged = mergeResult.getMergedSnapshot();

            // A choice the user already made must never be offered again, wherever it was made.
            java.util.Set<String> settledVersionIds =
                    capSettledVersionIds(
                            store.getResolvedAlternativeIds(),
                            remoteResult.getResolvedAlternativeIds());

            java.util.List<SyncMergeResult.Conflict> allConflicts = new java.util.ArrayList<>();
            for (SyncMergeResult.Conflict conflict : remoteResult.getConflicts()) {
                if (!settledVersionIds.contains(conflict.getLoserVersionId())) {
                    allConflicts.add(conflict);
                }
            }
            for (SyncMergeResult.Conflict conflict : mergeResult.getConflicts()) {
                if (!settledVersionIds.contains(conflict.getLoserVersionId())) {
                    allConflicts.add(conflict);
                }
            }

            // A conflict reported by the backend names the winner of the *remote* fold, which
            // is not necessarily the version this sync ends up applying. Persisting it unchanged
            // made "keep the version the merge selected" write a stale version over the live one.
            allConflicts = realignWinners(allConflicts, merged, local);

            // Every still-open alternative is republished, so a merged descendant can never be
            // the thing that makes a losing version unreachable.
            Map<String, SyncRecord> alternatives = new java.util.LinkedHashMap<>();
            for (SyncMergeResult.Conflict conflict : allConflicts) {
                alternatives.putIfAbsent(conflict.getLoserVersionId(), conflict.getLoser());
            }
            for (SyncRecord carried : remoteResult.getAlternatives()) {
                String versionId = carried.getCanonicalPayloadHash();
                if (!settledVersionIds.contains(versionId)) {
                    alternatives.putIfAbsent(versionId, carried);
                }
            }

            Map<String, Long> expectedSizes = attachmentSizes(merged);
            // The merged snapshot contains only the deterministic winner. A conflict row is not
            // durable unless the loser can later be restored as well, so preflight and pin each
            // version independently; SyncSnapshot deliberately forbids two versions of one ID.
            java.util.Set<String> synchronizedHashes = new java.util.HashSet<>();
            synchronizeAttachments(backend, merged, expectedSizes, synchronizedHashes);
            for (SyncMergeResult.Conflict conflict : allConflicts) {
                // Best effort. The merged snapshot's own blobs are mandatory and were just
                // transferred above; these are the extra copies that let a conflict be resolved
                // later. An alternative whose bytes have gone from Drive is already beyond
                // recovery, and failing here made that one missing blob stop every device from
                // syncing anything at all, including the devices that could never resolve it.
                // Resolution still verifies before it applies, so a version that cannot be
                // materialized simply cannot be chosen.
                pinConflictVersionQuietly(backend, conflict.getWinner(), synchronizedHashes);
                pinConflictVersionQuietly(backend, conflict.getLoser(), synchronizedHashes);
            }

            // What goes to Drive omits deletions every device has long had the chance to see;
            // the local apply below still receives all of them.
            SyncSnapshot published = forPublication(merged, remote, alternatives.values());
            if (needsPublication(
                    published,
                    withoutExpiredTombstones(remote, alternatives.values()),
                    alternatives.keySet(),
                    settledVersionIds,
                    remoteResult)) {
                backend.publish(
                        new SyncPublication(
                                published,
                                new java.util.ArrayList<>(alternatives.values()),
                                settledVersionIds,
                                remoteResult));
            }
            SyncState success =
                    SyncState.success(backendIdentifier, clock.instant(), allConflicts.size());
            store.applySnapshot(merged, allConflicts, success);
            return success;
        } catch (Exception exception) {
            SyncState failure =
                    SyncState.error(
                            backendIdentifier,
                            previousState.getLastSuccessfulSyncAt(),
                            safeErrorMessage(exception));
            persistState(failure);
            return failure;
        }
    }

    /**
     * Flags a device clock that disagrees badly with the rest of the account.
     *
     * <p>Merging is last-write-wins on wall-clock time. Per record that self-corrects: {@code
     * SyncMetadataDao.touch} assigns {@code max(now, storedUpdatedAt + 1)}, so once a device has
     * seen a newer remote version its own next edit outranks it however far behind its clock runs.
     * What stays exposed is the first divergent edit to a record neither side has synced since,
     * where the raw clocks decide and the slower device loses silently. A wrong device clock is
     * therefore worth a line in the log when support has to explain a "lost" edit.
     */
    private void warnAboutClockSkew(@NonNull SyncSnapshot remote) {
        Instant newest = null;
        for (SyncRecord record : remote.getRecords()) {
            if (newest == null || record.getUpdatedAt().isAfter(newest)) {
                newest = record.getUpdatedAt();
            }
        }
        if (newest == null) {
            return;
        }
        long skewMillis = newest.toEpochMilli() - clock.millis();
        if (skewMillis > MAX_TOLERATED_CLOCK_SKEW_MILLIS) {
            Log.w(
                    TAG,
                    "Remote records are "
                            + (skewMillis / 3_600_000L)
                            + "h ahead of this device's clock; merge order may be wrong");
        }
    }

    /**
     * Bounds the set of settled versions a bundle carries.
     *
     * <p>Every resolution adds its two version identities and they were never dropped, so the array
     * grew for the life of the account. Past the schema's record limit {@code encode} refuses the
     * bundle and every publish fails permanently, with nothing the user can do about it. The worst
     * case for a dropped identity is that an already-settled conflict is offered again, which is
     * recoverable, whereas a bundle that cannot be published is not — so the oldest go first.
     *
     * <p>The store's own settlements come newest first, so it is the oldest of them that go. The
     * bundle carries no settlement time, so the remote ones come in wire order; they are
     * interleaved with the local ones, so neither this device's own settlements nor everyone else's
     * can crowd the other side out entirely.
     */
    @NonNull
    static java.util.Set<String> capSettledVersionIds(
            @NonNull java.util.Collection<String> local,
            @NonNull java.util.Collection<String> remote) {
        java.util.Set<String> merged = new java.util.LinkedHashSet<>();
        java.util.Iterator<String> localIds = local.iterator();
        java.util.Iterator<String> remoteIds = remote.iterator();
        while (localIds.hasNext() || remoteIds.hasNext()) {
            if (localIds.hasNext()) merged.add(localIds.next());
            if (remoteIds.hasNext()) merged.add(remoteIds.next());
        }
        if (merged.size() <= MAX_PUBLISHED_SETTLED_IDS) {
            return merged;
        }
        Log.w(
                TAG,
                "Trimming "
                        + merged.size()
                        + " settled conflict versions to the publishable limit");
        java.util.Set<String> trimmed = new java.util.LinkedHashSet<>();
        for (String versionId : merged) {
            if (trimmed.size() >= MAX_PUBLISHED_SETTLED_IDS) {
                break;
            }
            trimmed.add(versionId);
        }
        return trimmed;
    }

    /**
     * The merged snapshot as it is published: without the tombstones nobody needs to be told about.
     *
     * <p>Tombstones were never dropped, and every one of them went into every bundle. The schema
     * caps a bundle at {@link SyncBundleValidator#MAX_RECORD_COUNT} records, live and deleted
     * together, so a library that had seen enough deletions over its lifetime failed every publish
     * from then on, permanently and on every device.
     *
     * <p>The rule, and why it cannot bring a deleted record back:
     *
     * <ul>
     *   <li>A tombstone is published while its deletion is younger than {@link
     *       #TOMBSTONE_PUBLICATION_HORIZON_MILLIS}. Any device that syncs within that window learns
     *       of the deletion directly.
     *   <li>Past it, the tombstone is published only when this sync read a live version of the same
     *       record from Drive, or an open conflict alternative for it is carried along. A device
     *       that slept through the horizon and republishes its stale live copy therefore gets the
     *       tombstone back from the next device that syncs; the merge sees a deletion newer than
     *       that copy and removes it there too, exactly as it would have without pruning. An edit
     *       made after the deletion is newer and wins, which is the ordinary rule as well.
     *   <li>Pruning is publication-only. Every device keeps its tombstone rows in {@code
     *       sync_metadata} for good — they are what lets it answer a stale copy as above — and
     *       still applies and merges them locally; only the bundle stops carrying them.
     * </ul>
     *
     * <p>The one window left: a fresh device installed while a stale copy is the only thing in
     * Drive shows that record until any device holding the tombstone next syncs.
     */
    @NonNull
    private SyncSnapshot forPublication(
            @NonNull SyncSnapshot merged,
            @NonNull SyncSnapshot remote,
            @NonNull Collection<SyncRecord> alternatives) {
        long horizon = clock.millis() - TOMBSTONE_PUBLICATION_HORIZON_MILLIS;
        java.util.Set<String> contested = new java.util.HashSet<>();
        for (SyncRecord alternative : alternatives) {
            contested.add(alternative.getType().getWireValue() + ":" + alternative.getId());
        }
        java.util.List<SyncRecord> kept = new java.util.ArrayList<>();
        boolean pruned = false;
        for (SyncRecord record : merged.getRecords()) {
            if (record.isTombstone()
                    && record.getDeletedAt().toEpochMilli() < horizon
                    && !contested.contains(
                            record.getType().getWireValue() + ":" + record.getId())) {
                SyncRecord remoteRecord = remote.find(record.getType(), record.getId());
                if (remoteRecord == null || remoteRecord.isTombstone()) {
                    pruned = true;
                    continue;
                }
            }
            kept.add(record);
        }
        return pruned ? new SyncSnapshot(kept) : merged;
    }

    /**
     * {@code remote} as this version would have published it: without the expired tombstones {@link
     * #forPublication} leaves out.
     *
     * <p>Only for deciding whether to publish. An older app version on another device keeps every
     * tombstone and puts the expired ones back in each bundle it writes; compared with those, the
     * pruned snapshot never matched, and both devices republished the whole bundle on every sync,
     * each undoing the other. A remote that differs only by tombstones nobody needs any more
     * already says everything this sync would say. A tombstone {@code forPublication} keeps — one
     * that is contested, or whose record Drive still holds live — is not a tombstone in {@code
     * remote} or is kept here as well.
     */
    @NonNull
    private SyncSnapshot withoutExpiredTombstones(
            @NonNull SyncSnapshot remote, @NonNull Collection<SyncRecord> alternatives) {
        long horizon = clock.millis() - TOMBSTONE_PUBLICATION_HORIZON_MILLIS;
        java.util.Set<String> contested = new java.util.HashSet<>();
        for (SyncRecord alternative : alternatives) {
            contested.add(alternative.getType().getWireValue() + ":" + alternative.getId());
        }
        java.util.List<SyncRecord> kept = new java.util.ArrayList<>();
        boolean pruned = false;
        for (SyncRecord record : remote.getRecords()) {
            if (record.isTombstone()
                    && record.getDeletedAt().toEpochMilli() < horizon
                    && !contested.contains(
                            record.getType().getWireValue() + ":" + record.getId())) {
                pruned = true;
                continue;
            }
            kept.add(record);
        }
        return pruned ? new SyncSnapshot(kept) : remote;
    }

    /**
     * Re-points every conflict at the version this sync actually applies.
     *
     * <p>{@code KEEP_WINNER} promises the version the deterministic merge selected. The remote
     * backend reports conflicts from folding Drive's heads together, before local state is
     * considered, so its "winner" can be a version the final merge rejected. Left alone, choosing
     * "keep winner" reverted the record to that rejected version and republished it everywhere.
     *
     * <p>A conflict whose winner and alternative collapse to the same version is dropped: there is
     * nothing left for the user to choose between.
     */
    @NonNull
    private static java.util.List<SyncMergeResult.Conflict> realignWinners(
            @NonNull java.util.List<SyncMergeResult.Conflict> conflicts,
            @NonNull SyncSnapshot merged,
            @NonNull SyncSnapshot local) {
        java.util.List<SyncMergeResult.Conflict> aligned = new java.util.ArrayList<>();
        for (SyncMergeResult.Conflict conflict : conflicts) {
            SyncRecord winner = merged.find(conflict.getType(), conflict.getId());
            if (winner == null) {
                aligned.add(conflict);
                continue;
            }
            String winnerVersion = winner.getCanonicalPayloadHash();
            if (winnerVersion.equals(conflict.getLoserVersionId())) {
                continue;
            }
            if (winnerVersion.equals(conflict.getWinnerVersionId())) {
                aligned.add(conflict);
                continue;
            }
            SyncRecord localRecord = local.find(conflict.getType(), conflict.getId());
            SyncMergeResult.Source winnerSource =
                    localRecord != null
                                    && localRecord.getCanonicalPayloadHash().equals(winnerVersion)
                            ? SyncMergeResult.Source.LOCAL
                            : SyncMergeResult.Source.REMOTE;
            aligned.add(
                    new SyncMergeResult.Conflict(
                            winner, conflict.getLoser(), winnerSource, conflict.getLoserSource()));
        }
        return aligned;
    }

    /**
     * Whether the remote state already says everything this sync would say.
     *
     * <p>Records alone are not enough: an unchanged record set with a newly discovered alternative,
     * or with a conflict the user has just resolved, still has to be published or that information
     * exists on one device only.
     */
    private static boolean needsPublication(
            @NonNull SyncSnapshot merged,
            @NonNull SyncSnapshot remote,
            @NonNull Collection<String> alternativeVersionIds,
            @NonNull java.util.Set<String> settledVersionIds,
            @NonNull RemoteSnapshot remoteResult) {
        if (!snapshotsMatch(merged, remote)) {
            return true;
        }
        java.util.Set<String> publishedAlternatives = new java.util.LinkedHashSet<>();
        for (SyncRecord alternative : remoteResult.getAlternatives()) {
            publishedAlternatives.add(alternative.getCanonicalPayloadHash());
        }
        return !publishedAlternatives.equals(new java.util.LinkedHashSet<>(alternativeVersionIds))
                || !remoteResult.getResolvedAlternativeIds().equals(settledVersionIds);
    }

    private static boolean snapshotsMatch(
            @NonNull SyncSnapshot first, @NonNull SyncSnapshot second) {
        Collection<SyncRecord> firstRecords = first.getRecords();
        Collection<SyncRecord> secondRecords = second.getRecords();
        if (firstRecords.size() != secondRecords.size()) {
            return false;
        }
        java.util.Iterator<SyncRecord> firstIterator = firstRecords.iterator();
        java.util.Iterator<SyncRecord> secondIterator = secondRecords.iterator();
        while (firstIterator.hasNext()) {
            SyncRecord firstRecord = firstIterator.next();
            SyncRecord secondRecord = secondIterator.next();
            if (firstRecord.getType() != secondRecord.getType()
                    || !firstRecord.getId().equals(secondRecord.getId())
                    || !firstRecord
                            .getCanonicalPayloadHash()
                            .equals(secondRecord.getCanonicalPayloadHash())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Makes every blob {@code merged} references available, verified, at both endpoints.
     *
     * @param synchronizedHashes blobs already settled by an earlier call this sync, skipped here
     *     and extended with the ones settled now. A conflict version shares most of its blobs with
     *     the merged snapshot, and re-verifying each one meant re-hashing and re-downloading it
     *     once per version while the conflict stayed open.
     */
    private void synchronizeAttachments(
            SyncBackend backend,
            SyncSnapshot merged,
            Map<String, Long> expectedSizes,
            java.util.Set<String> synchronizedHashes)
            throws IOException {
        Collection<String> hashes =
                Objects.requireNonNull(store.getAttachmentHashes(merged), "hashes");
        for (String hash : hashes) {
            validateHash(hash);
            if (!synchronizedHashes.add(hash)) {
                continue;
            }
            if (store.hasAttachment(hash)) {
                Long expectedSize = expectedSizes.get(hash);
                // Index lookup only; the backend remembers the listing, so the verification
                // below does not list the same blob again.
                boolean remotePresent = backend.hasAttachment(hash);
                // Through the store's digest cache: reading and hashing every local blob on every
                // sync, unchanged or not, was the bulk of an idle sync's work.
                if (!store.hasVerifiedAttachment(hash, expectedSize)) {
                    if (!remotePresent) {
                        // Nothing to repair it from. Read it in full, which raises the precise
                        // failure — missing, wrong size or wrong digest — for the user to see.
                        verifyAttachment(hash, expectedSize, store.readAttachment(hash));
                    } else {
                        // The local copy is missing or corrupt; repair it from the remote blob,
                        // which copyVerified refuses to accept unless it hashes correctly.
                        copyVerified(
                                hash,
                                expectedSize,
                                backend.readAttachment(hash),
                                store::writeAttachment);
                    }
                }
                // Drive is untrusted: a matching appProperty is only a claim. The blob is read
                // and hashed exactly once per sync, and the result is remembered, so publishing
                // it into the canonical root does not download it again.
                if (!remotePresent) {
                    copyVerified(
                            hash,
                            expectedSize,
                            store.readAttachment(hash),
                            backend::writeAttachment);
                } else if (!backend.hasVerifiedAttachment(hash, expectedSize)) {
                    // Present but corrupt. Blobs are content-addressed and duplicates are
                    // tolerated — the reader picks a verified candidate — so publishing a fresh
                    // copy of the known-good local bytes repairs the account. Throwing here
                    // instead left every device failing every sync until someone deleted the bad
                    // object from Drive by hand. The replacement is confirmed before the bundle
                    // is allowed to depend on it.
                    copyVerified(
                            hash,
                            expectedSize,
                            store.readAttachment(hash),
                            backend::writeAttachment);
                    if (!backend.hasVerifiedAttachment(hash, expectedSize)) {
                        throw new AttachmentIntegrityException(
                                "Attachment checksum does not match its declared hash");
                    }
                }
            } else {
                InputStream remoteAttachment = backend.readAttachment(hash);
                if (remoteAttachment == null) {
                    throw unavailableAttachment(hash);
                }
                copyVerified(
                        hash, expectedSizes.get(hash), remoteAttachment, store::writeAttachment);
            }
        }
    }

    /** Pins a conflict version's blobs, logging rather than failing the whole sync. */
    private void pinConflictVersionQuietly(
            @NonNull SyncBackend backend,
            @NonNull SyncRecord record,
            @NonNull java.util.Set<String> synchronizedHashes) {
        try {
            pinConflictVersion(backend, record, synchronizedHashes);
        } catch (IOException unavailable) {
            Log.w(
                    TAG,
                    "Could not pin a conflict version's attachments; it stays unresolvable: "
                            + safeErrorMessage(unavailable));
        }
    }

    /**
     * Pins required conflict blobs into the store's durable content-addressed cache.
     *
     * <p>A blob already in that cache is left alone: copying it onto itself rewrote hundreds of
     * megabytes per sync for as long as a conflict on a large note stayed open.
     */
    private void pinConflictVersion(
            @NonNull SyncBackend backend,
            @NonNull SyncRecord record,
            @NonNull java.util.Set<String> synchronizedHashes)
            throws IOException {
        if (record.isTombstone()) return;
        SyncSnapshot snapshot = new SyncSnapshot(java.util.Collections.singletonList(record));
        Map<String, Long> expectedSizes = attachmentSizes(snapshot);
        synchronizeAttachments(backend, snapshot, expectedSizes, synchronizedHashes);
        for (String hash : store.getAttachmentHashes(snapshot)) {
            Long expectedSize = expectedSizes.get(hash);
            if (store.hasDurableAttachment(hash, expectedSize == null ? -1L : expectedSize)) {
                continue;
            }
            copyVerified(hash, expectedSize, store.readAttachment(hash), store::writeAttachment);
        }
    }

    /**
     * The failure for a blob neither endpoint holds.
     *
     * <p>When the store published the attachment from remembered metadata — its file gone, the
     * bytes expected back from the remote — the message names the note, as a build failure would
     * have; a hash alone left the user with a permanently failing sync and no way to find the note.
     */
    @NonNull
    private IOException unavailableAttachment(@NonNull String hash) {
        SnapshotProblem problem = null;
        try {
            problem = store.describeMissingAttachment(hash);
        } catch (RuntimeException ignored) {
            // The description is a courtesy; the failure below stands without it.
        }
        return problem != null
                ? SnapshotBuildResult.incompleteBecause(problem)
                : new IOException("Required attachment is unavailable: " + hash);
    }

    private void verifyAttachment(String hash, Long expectedSize, InputStream source)
            throws IOException {
        if (source == null) {
            throw unavailableAttachment(hash);
        }
        // Consume the complete blob before accepting an existing attachment.
        try (InputStream input = source) {
            VerifyingInputStream.verify(input, hash, expectedSize);
        }
    }

    /**
     * Pipes one blob to the other endpoint, verifying it as the bytes go past.
     *
     * <p>This used to buffer the whole blob into a {@code ByteArrayOutputStream}, call {@code
     * toByteArray()} and hand the destination a {@code ByteArrayInputStream}. With the 100 MB
     * attachment ceiling that peaked at several hundred megabytes of heap for a single file — an
     * {@code OutOfMemoryError} on any ordinary phone. Nothing is buffered now: {@link
     * VerifyingInputStream} checks the digest at end of stream, which happens inside the
     * destination's own read loop, so a corrupt blob still aborts the write before it is committed.
     */
    private void copyVerified(
            String expectedHash,
            Long expectedSize,
            InputStream source,
            AttachmentWriter destination)
            throws IOException {
        if (source == null) {
            throw new IOException("Required attachment is unavailable: " + expectedHash);
        }
        try (InputStream input = source;
                VerifyingInputStream verified =
                        new VerifyingInputStream(input, expectedHash, expectedSize)) {
            destination.write(expectedHash, expectedSize == null ? -1L : expectedSize, verified);
            verified.verifyEndOfStream();
        }
    }

    private static Map<String, Long> attachmentSizes(SyncSnapshot snapshot) throws IOException {
        Map<String, Long> sizes = new HashMap<>();
        for (SyncRecord record : snapshot.getLiveRecords(SyncRecord.Type.NOTE)) {
            JsonArray manifest = record.getPayload().getAsJsonArray("attachmentsManifest");
            if (manifest == null) continue;
            for (JsonElement element : manifest) {
                if (!element.isJsonObject()) continue;
                JsonObject value = element.getAsJsonObject();
                if (!value.has("sha256") || !value.has("size")) continue;
                String hash = value.get("sha256").getAsString();
                long size = value.get("size").getAsLong();
                if (size < 0L || size > SyncBundleValidator.MAX_ATTACHMENT_BYTES) {
                    throw new IOException("Attachment size exceeds the sync limit");
                }
                Long previous = sizes.putIfAbsent(hash, size);
                if (previous != null && previous.longValue() != size) {
                    throw new IOException("Attachment metadata has conflicting sizes");
                }
            }
        }
        return sizes;
    }

    private SyncState safeReadState() {
        try {
            SyncState state = store.readState();
            return state == null ? SyncState.idle() : state;
        } catch (Exception ignored) {
            return SyncState.idle();
        }
    }

    private void persistState(SyncState state) {
        try {
            store.writeState(state);
        } catch (Exception ignored) {
            // Content sync remains valid when only the user-visible status store is temporarily
            // unavailable.
        }
    }

    @NonNull
    private static String requireBackendIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Sync backend identifier must not be empty");
        }
        return identifier.trim();
    }

    @NonNull
    private static String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName()
                : message.trim();
    }

    private static void validateHash(String hash) throws IOException {
        if (hash == null || !SHA_256.matcher(hash).matches()) {
            throw new IOException("Attachment hash must be a lowercase SHA-256 value");
        }
    }

    private interface AttachmentWriter {
        void write(String hash, long sizeBytes, InputStream content) throws IOException;
    }
}
