package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable result of reading a remote causal frontier.
 *
 * <p>Also the read context a publish must quote back. {@code writeSnapshot} used to take its causal
 * parents from a mutable field on the backend, so a write with no preceding read silently published
 * a parentless root that forked the DAG for good. The token here makes that mistake loud.
 */
public final class RemoteSnapshot {
    private final SyncSnapshot snapshot;
    private final List<SyncMergeResult.Conflict> conflicts;
    private final List<String> frontierBundleIds;
    private final List<SyncRecord> alternatives;
    private final Set<String> resolvedAlternativeIds;
    private final String readToken;

    public RemoteSnapshot(
            @NonNull SyncSnapshot snapshot,
            @NonNull List<SyncMergeResult.Conflict> conflicts,
            @NonNull List<String> frontierBundleIds) {
        this(
                snapshot,
                conflicts,
                frontierBundleIds,
                Collections.emptyList(),
                Collections.emptySet(),
                "");
    }

    public RemoteSnapshot(
            @NonNull SyncSnapshot snapshot,
            @NonNull List<SyncMergeResult.Conflict> conflicts,
            @NonNull List<String> frontierBundleIds,
            @NonNull List<SyncRecord> alternatives,
            @NonNull Set<String> resolvedAlternativeIds,
            @NonNull String readToken) {
        this.snapshot = snapshot;
        this.conflicts = Collections.unmodifiableList(new ArrayList<>(conflicts));
        this.frontierBundleIds = Collections.unmodifiableList(new ArrayList<>(frontierBundleIds));
        this.alternatives = Collections.unmodifiableList(new ArrayList<>(alternatives));
        this.resolvedAlternativeIds =
                Collections.unmodifiableSet(new LinkedHashSet<>(resolvedAlternativeIds));
        this.readToken = readToken;
    }

    @NonNull
    public static RemoteSnapshot of(@NonNull SyncSnapshot snapshot) {
        return new RemoteSnapshot(snapshot, Collections.emptyList(), Collections.emptyList());
    }

    @NonNull
    public SyncSnapshot getSnapshot() {
        return snapshot;
    }

    @NonNull
    public List<SyncMergeResult.Conflict> getConflicts() {
        return conflicts;
    }

    @NonNull
    public List<String> getFrontierBundleIds() {
        return frontierBundleIds;
    }

    /** Losing versions the remote state still keeps recoverable. */
    @NonNull
    public List<SyncRecord> getAlternatives() {
        return alternatives;
    }

    /** Version identities some device has recorded as explicitly resolved. */
    @NonNull
    public Set<String> getResolvedAlternativeIds() {
        return resolvedAlternativeIds;
    }

    /** Opaque proof that this read happened, quoted back by the matching publish. */
    @NonNull
    public String getReadToken() {
        return readToken;
    }
}
