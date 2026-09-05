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

    /**
     * The failure for a problem discovered after the build — a blob the build described from
     * remembered metadata that no endpoint turned out to hold — worded like a build failure so the
     * user reads the same "which note" message.
     */
    @NonNull
    public static SnapshotBuildException incompleteBecause(@NonNull SnapshotProblem problem) {
        return new SnapshotBuildException(Collections.singletonList(problem));
    }

    /** Typed, coarse error suitable for persisted sync state and telemetry. */
    public static final class SnapshotBuildException extends IOException {
        @NonNull private final List<SnapshotProblem> problems;

        private SnapshotBuildException(@NonNull List<SnapshotProblem> problems) {
            // The first problem names its record: a user who reads this on the account screen
            // has to know which note to open, not only that some attachment somewhere is gone.
            super("Local snapshot is incomplete: " + problems.get(0).describe());
            this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
        }

        @NonNull
        public List<SnapshotProblem> getProblems() {
            return problems;
        }
    }
}
