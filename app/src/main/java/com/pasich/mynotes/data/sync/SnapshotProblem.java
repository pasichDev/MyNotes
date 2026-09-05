package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

/** A privacy-safe reason why a local snapshot cannot safely be published. */
public final class SnapshotProblem {

    public enum Kind {
        MISSING_ATTACHMENT,
        UNREADABLE_ATTACHMENT,
        ATTACHMENT_HASH_FAILED,
        INVALID_ATTACHMENT_METADATA
    }

    private static final int MAX_LABEL_LENGTH = 40;

    @NonNull private final Kind kind;
    @NonNull private final String recordType;
    @NonNull private final String stableId;
    @Nullable private final String label;

    public SnapshotProblem(
            @NonNull Kind kind, @NonNull String recordType, @NonNull String stableId) {
        this(kind, recordType, stableId, null);
    }

    /**
     * @param label what the user calls the record — a note's title — so the failure names the note
     *     to fix instead of leaving them to guess. Stays on the device: it is shown on the account
     *     screen and kept in the local sync state, never published.
     */
    public SnapshotProblem(
            @NonNull Kind kind,
            @NonNull String recordType,
            @NonNull String stableId,
            @Nullable String label) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.recordType = Objects.requireNonNull(recordType, "recordType");
        this.stableId = Objects.requireNonNull(stableId, "stableId");
        this.label = label == null || label.trim().isEmpty() ? null : truncate(label.trim());
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

    @Nullable
    public String getLabel() {
        return label;
    }

    /** One line naming the failure and, when known, the record it is in. */
    @NonNull
    public String describe() {
        return label == null
                ? kind.name()
                : kind.name() + " in " + recordType + " \"" + label + "\"";
    }

    private static String truncate(String value) {
        return value.length() <= MAX_LABEL_LENGTH
                ? value
                : value.substring(0, MAX_LABEL_LENGTH) + "…";
    }
}
