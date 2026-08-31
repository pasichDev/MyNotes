package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.time.Instant;
import java.util.Objects;

/** Immutable, persistable summary of the last manual synchronization attempt. */
public final class SyncState {

    public enum Status {
        IDLE,
        SYNCING,
        SUCCESS,
        ERROR
    }

    private final Status status;
    @Nullable private final String backendIdentifier;
    @Nullable private final Instant lastSuccessfulSyncAt;
    @Nullable private final Instant attemptStartedAt;
    @Nullable private final String errorMessage;
    private final int conflictCount;

    private SyncState(
            @NonNull Status status,
            @Nullable String backendIdentifier,
            @Nullable Instant lastSuccessfulSyncAt,
            @Nullable Instant attemptStartedAt,
            @Nullable String errorMessage,
            int conflictCount) {
        this.status = Objects.requireNonNull(status, "status");
        this.backendIdentifier = emptyToNull(backendIdentifier);
        this.lastSuccessfulSyncAt = lastSuccessfulSyncAt;
        this.attemptStartedAt = attemptStartedAt;
        this.errorMessage = emptyToNull(errorMessage);
        if (conflictCount < 0) {
            throw new IllegalArgumentException("conflictCount must not be negative");
        }
        this.conflictCount = conflictCount;
        validate();
    }

    @NonNull
    public static SyncState idle() {
        return new SyncState(Status.IDLE, null, null, null, null, 0);
    }

    @NonNull
    public static SyncState syncing(@NonNull String backendIdentifier, @NonNull Instant startedAt) {
        return syncing(backendIdentifier, startedAt, null);
    }

    /**
     * A sync in progress, remembering when the last successful one happened.
     *
     * <p>Without carrying it, starting a sync erased the only record of the previous success: the
     * screen then read "never synced" for the whole attempt, and permanently if the process died
     * before the attempt finished.
     */
    public static SyncState syncing(
            @NonNull String backendIdentifier,
            @NonNull Instant startedAt,
            @Nullable Instant lastSuccessfulSyncAt) {
        return new SyncState(
                Status.SYNCING, backendIdentifier, lastSuccessfulSyncAt, startedAt, null, 0);
    }

    @NonNull
    public static SyncState success(
            @NonNull String backendIdentifier, @NonNull Instant completedAt, int conflictCount) {
        return new SyncState(
                Status.SUCCESS, backendIdentifier, completedAt, null, null, conflictCount);
    }

    @NonNull
    public static SyncState error(
            @NonNull String backendIdentifier,
            @Nullable Instant lastSuccessfulSyncAt,
            @NonNull String errorMessage) {
        return new SyncState(
                Status.ERROR, backendIdentifier, lastSuccessfulSyncAt, null, errorMessage, 0);
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    @Nullable
    public String getBackendIdentifier() {
        return backendIdentifier;
    }

    @Nullable
    public Instant getLastSuccessfulSyncAt() {
        return lastSuccessfulSyncAt;
    }

    @Nullable
    public Instant getAttemptStartedAt() {
        return attemptStartedAt;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    private void validate() {
        if (status == Status.IDLE) {
            return;
        }
        if (backendIdentifier == null) {
            throw new IllegalArgumentException(
                    "A non-idle sync state requires a backend identifier");
        }
        if (status == Status.SYNCING && attemptStartedAt == null) {
            throw new IllegalArgumentException("A syncing state requires a start time");
        }
        if (status == Status.SUCCESS && lastSuccessfulSyncAt == null) {
            throw new IllegalArgumentException("A success state requires a completion time");
        }
        if (status == Status.ERROR && errorMessage == null) {
            throw new IllegalArgumentException("An error state requires an error message");
        }
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
