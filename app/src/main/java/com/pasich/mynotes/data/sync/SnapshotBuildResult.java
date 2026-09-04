package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of building a local sync snapshot.
 *
 * <p>An unpublishable result is deliberately not convertible into a {@link SyncSnapshot}. This
 * prevents a caller from accidentally publishing a note after attachment collection failed.
 */
public final class SnapshotBuildResult {

    @NonNull private final SyncSnapshot snapshot;
    @NonNull private final List<SnapshotProblem> problems;

    private SnapshotBuildResult(
            @NonNull SyncSnapshot snapshot, @NonNull List<SnapshotProblem> problems) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
    }

    @NonNull
    public static SnapshotBuildResult publishable(@NonNull SyncSnapshot snapshot) {
        return new SnapshotBuildResult(snapshot, Collections.emptyList());
    }

    @NonNull
    public static SnapshotBuildResult incomplete(
            @NonNull SyncSnapshot snapshot, @NonNull List<SnapshotProblem> problems) {
        if (problems.isEmpty()) {
            throw new IllegalArgumentException("An incomplete snapshot requires a problem");
        }
        return new SnapshotBuildResult(snapshot, problems);
    }

    public boolean isPublishable() {
        return problems.isEmpty();
    }

    @NonNull
    public List<SnapshotProblem> getProblems() {
        return problems;
    }

    /** Returns the snapshot only when all local attachment references were verified. */
    @NonNull
    public SyncSnapshot requireSnapshot() throws IOException {
        if (!isPublishable()) {
            throw new SnapshotBuildException(problems);
        }
        return snapshot;
    }

    /** Typed, coarse error suitable for persisted sync state and telemetry. */
    public static final class SnapshotBuildException extends IOException {
        @NonNull private final List<SnapshotProblem> problems;

        private SnapshotBuildException(@NonNull List<SnapshotProblem> problems) {
            super("Local snapshot is incomplete: " + problems.get(0).getKind().name());
            this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
        }

        @NonNull
        public List<SnapshotProblem> getProblems() {
            return problems;
        }
    }
}
