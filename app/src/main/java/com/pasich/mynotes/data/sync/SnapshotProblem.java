package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import java.util.Objects;

/** A privacy-safe reason why a local snapshot cannot safely be published. */
public final class SnapshotProblem {

    public enum Kind {
        MISSING_ATTACHMENT,
        UNREADABLE_ATTACHMENT,
        ATTACHMENT_HASH_FAILED,
        INVALID_ATTACHMENT_METADATA
    }

    @NonNull private final Kind kind;
    @NonNull private final String recordType;
    @NonNull private final String stableId;

    public SnapshotProblem(
            @NonNull Kind kind, @NonNull String recordType, @NonNull String stableId) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.recordType = Objects.requireNonNull(recordType, "recordType");
        this.stableId = Objects.requireNonNull(stableId, "stableId");
    }

    @NonNull
    public Kind getKind() {
        return kind;
    }

    @NonNull
    public String getRecordType() {
        return recordType;
    }

    @NonNull
    public String getStableId() {
        return stableId;
    }
}
