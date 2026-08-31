package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Result of a deterministic snapshot merge, including every version that was not selected. */
public final class SyncMergeResult {

    /** Indicates the endpoint from which a selected version came. */
    public enum Source {
        LOCAL,
        REMOTE
    }

    /** A differing losing version retained for user-visible or persisted conflict reporting. */
    public static final class Conflict {
        private final SyncRecord.Type type;
        private final String id;
        private final SyncRecord winner;
        private final SyncRecord loser;
        private final Source winnerSource;

        Conflict(
                @NonNull SyncRecord winner,
                @NonNull SyncRecord loser,
                @NonNull Source winnerSource) {
            this.winner = Objects.requireNonNull(winner, "winner");
            this.loser = Objects.requireNonNull(loser, "loser");
            this.winnerSource = Objects.requireNonNull(winnerSource, "winnerSource");
            if (winner.getType() != loser.getType() || !winner.getId().equals(loser.getId())) {
                throw new IllegalArgumentException("A conflict must refer to one record identity");
            }
            this.type = winner.getType();
            this.id = winner.getId();
        }

        @NonNull
        public SyncRecord.Type getType() {
            return type;
        }

        @NonNull
        public String getId() {
            return id;
        }

        @NonNull
        public SyncRecord getWinner() {
            return winner;
        }

        @NonNull
        public SyncRecord getLoser() {
            return loser;
        }

        @NonNull
        public Source getWinnerSource() {
            return winnerSource;
        }

        public boolean isWinnerTombstone() {
            return winner.isTombstone();
        }

        public boolean isLoserTombstone() {
            return loser.isTombstone();
        }
    }

    private final SyncSnapshot mergedSnapshot;
    private final List<Conflict> conflicts;

    SyncMergeResult(@NonNull SyncSnapshot mergedSnapshot, @NonNull List<Conflict> conflicts) {
        this.mergedSnapshot = Objects.requireNonNull(mergedSnapshot, "mergedSnapshot");
        this.conflicts = Collections.unmodifiableList(new ArrayList<>(conflicts));
    }

    @NonNull
    public SyncSnapshot getMergedSnapshot() {
        return mergedSnapshot;
    }

    @NonNull
    public List<Conflict> getConflicts() {
        return conflicts;
    }

    /** Convenience view for repository code that persists discarded versions separately. */
    @NonNull
    public List<SyncRecord> getDiscardedRecords() {
        List<SyncRecord> discarded = new ArrayList<>(conflicts.size());
        for (Conflict conflict : conflicts) {
            discarded.add(conflict.getLoser());
        }
        return Collections.unmodifiableList(discarded);
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }
}
