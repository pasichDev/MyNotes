package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable result of reading a remote causal frontier. */
public final class RemoteSnapshot {
    private final SyncSnapshot snapshot;
    private final List<SyncMergeResult.Conflict> conflicts;
    private final List<String> frontierBundleIds;

    public RemoteSnapshot(
            @NonNull SyncSnapshot snapshot,
            @NonNull List<SyncMergeResult.Conflict> conflicts,
            @NonNull List<String> frontierBundleIds) {
        this.snapshot = snapshot;
        this.conflicts = Collections.unmodifiableList(new ArrayList<>(conflicts));
        this.frontierBundleIds = Collections.unmodifiableList(new ArrayList<>(frontierBundleIds));
    }

    @NonNull
    public static RemoteSnapshot of(@NonNull SyncSnapshot snapshot) {
        return new RemoteSnapshot(snapshot, Collections.emptyList(), Collections.emptyList());
    }

    @NonNull public SyncSnapshot getSnapshot() { return snapshot; }
    @NonNull public List<SyncMergeResult.Conflict> getConflicts() { return conflicts; }
    @NonNull public List<String> getFrontierBundleIds() { return frontierBundleIds; }
}
