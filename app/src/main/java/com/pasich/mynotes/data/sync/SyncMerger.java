package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Provider-independent merge engine for snapshots.
 *
 * <p>A missing version never means deletion. For a record present at both endpoints, newest {@code
 * updatedAt} wins. Equal but different versions use the lexicographically smaller canonical SHA-256
 * as a stable tiebreaker, so every device reaches the same result regardless of merge order.
 */
public final class SyncMerger {

    @NonNull
    public SyncMergeResult merge(@NonNull SyncSnapshot local, @NonNull SyncSnapshot remote) {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(remote, "remote");

        Map<SyncSnapshot.RecordKey, SyncRecord> allLocal = local.asMap();
        Map<SyncSnapshot.RecordKey, SyncRecord> allRemote = remote.asMap();
        TreeMap<SyncSnapshot.RecordKey, SyncRecord> merged = new TreeMap<>();
        ArrayList<SyncMergeResult.Conflict> conflicts = new ArrayList<>();

        TreeMap<SyncSnapshot.RecordKey, Boolean> keys = new TreeMap<>();
        for (SyncSnapshot.RecordKey key : allLocal.keySet()) {
            keys.put(key, Boolean.TRUE);
        }
        for (SyncSnapshot.RecordKey key : allRemote.keySet()) {
            keys.put(key, Boolean.TRUE);
        }

        for (SyncSnapshot.RecordKey key : keys.keySet()) {
            SyncRecord localRecord = allLocal.get(key);
            SyncRecord remoteRecord = allRemote.get(key);
            if (localRecord == null) {
                merged.put(key, remoteRecord);
            } else if (remoteRecord == null) {
                merged.put(key, localRecord);
            } else {
                mergeVersions(localRecord, remoteRecord, merged, conflicts, key);
            }
        }
        return new SyncMergeResult(new SyncSnapshot(merged.values()), conflicts);
    }

    private void mergeVersions(
            SyncRecord local,
            SyncRecord remote,
            Map<SyncSnapshot.RecordKey, SyncRecord> merged,
            ArrayList<SyncMergeResult.Conflict> conflicts,
            SyncSnapshot.RecordKey key) {
        int timestampComparison = local.getUpdatedAt().compareTo(remote.getUpdatedAt());
        if (timestampComparison > 0) {
            merged.put(key, local);
            addConflictWhenDifferent(local, remote, SyncMergeResult.Source.LOCAL, conflicts);
            return;
        }
        if (timestampComparison < 0) {
            merged.put(key, remote);
            addConflictWhenDifferent(remote, local, SyncMergeResult.Source.REMOTE, conflicts);
            return;
        }

        String localHash = local.getCanonicalPayloadHash();
        String remoteHash = remote.getCanonicalPayloadHash();
        if (localHash.equals(remoteHash)) {
            merged.put(key, local);
        } else if (localHash.compareTo(remoteHash) < 0) {
            merged.put(key, local);
            conflicts.add(
                    new SyncMergeResult.Conflict(local, remote, SyncMergeResult.Source.LOCAL));
        } else {
            merged.put(key, remote);
            conflicts.add(
                    new SyncMergeResult.Conflict(remote, local, SyncMergeResult.Source.REMOTE));
        }
    }

    private void addConflictWhenDifferent(
            SyncRecord winner,
            SyncRecord loser,
            SyncMergeResult.Source winnerSource,
            ArrayList<SyncMergeResult.Conflict> conflicts) {
        if (!winner.getCanonicalPayloadHash().equals(loser.getCanonicalPayloadHash())) {
            conflicts.add(new SyncMergeResult.Conflict(winner, loser, winnerSource));
        }
    }
}
