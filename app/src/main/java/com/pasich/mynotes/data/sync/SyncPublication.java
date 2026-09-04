package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Everything one publish must carry, including the read it was derived from.
 *
 * <p>Bundling the causal context with the content makes the ordering rule explicit rather than a
 * convention: a backend can refuse a publish whose read context is missing or stale instead of
 * quietly writing a bundle with the wrong parents.
 */
public final class SyncPublication {

    private final SyncSnapshot snapshot;
    private final List<SyncRecord> unresolvedAlternatives;
    private final Set<String> resolvedAlternativeIds;
    private final RemoteSnapshot readContext;

    public SyncPublication(
            @NonNull SyncSnapshot snapshot,
            @NonNull List<SyncRecord> unresolvedAlternatives,
            @NonNull Set<String> resolvedAlternativeIds,
            @NonNull RemoteSnapshot readContext) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.unresolvedAlternatives =
                Collections.unmodifiableList(new ArrayList<>(unresolvedAlternatives));
        this.resolvedAlternativeIds =
                Collections.unmodifiableSet(new LinkedHashSet<>(resolvedAlternativeIds));
        this.readContext = Objects.requireNonNull(readContext, "readContext");
    }

    @NonNull
    public SyncSnapshot getSnapshot() {
        return snapshot;
    }

    @NonNull
    public List<SyncRecord> getUnresolvedAlternatives() {
        return unresolvedAlternatives;
    }

    @NonNull
    public Set<String> getResolvedAlternativeIds() {
        return resolvedAlternativeIds;
    }

    @NonNull
    public RemoteSnapshot getReadContext() {
        return readContext;
    }
}
