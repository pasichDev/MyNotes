package com.pasich.mynotes.utils.encly;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What Encly answered. A missing reason — Encly crashed, or the user pressed back before it could
 * answer — is read as a cancellation, never as a success.
 */
public final class HandoffResult {

    /** {@code android.app.Activity.RESULT_OK}; kept here so parsing stays a JVM concern. */
    public static final int RESULT_OK = -1;

    public enum Reason {
        UNTRUSTED_CALLER("untrusted_caller"),
        CANCELLED("cancelled"),
        UNSUPPORTED_SCHEMA("unsupported_schema"),
        INVALID_PAYLOAD("invalid_payload"),
        TOO_LARGE("too_large"),
        FAILED("failed");

        public final String wire;

        Reason(String wire) {
            this.wire = wire;
        }

        @NonNull
        static Reason fromWire(@Nullable String value) {
            if (value == null || value.isEmpty()) return CANCELLED;
            for (Reason reason : values()) {
                if (reason.wire.equals(value)) return reason;
            }
            // A reason this build does not know yet is still a failure, not a silent cancel.
            return FAILED;
        }
    }

    public final boolean success;
    public final int notes;
    public final int tasks;
    public final int tags;
    public final int skipped;

    /** Set only when {@link #success} is false. */
    @Nullable public final Reason reason;

    private HandoffResult(
            boolean success, int notes, int tasks, int tags, int skipped, @Nullable Reason reason) {
        this.success = success;
        this.notes = notes;
        this.tasks = tasks;
        this.tags = tags;
        this.skipped = skipped;
        this.reason = reason;
    }

    /**
     * @param resultCode the activity result code.
     * @param notes the {@code notes} extra, or {@code null} when absent; likewise for the rest.
     */
    @NonNull
    public static HandoffResult parse(
            int resultCode,
            @Nullable Integer notes,
            @Nullable Integer tasks,
            @Nullable Integer tags,
            @Nullable Integer skipped,
            @Nullable String reason) {
        if (resultCode == RESULT_OK) {
            return new HandoffResult(
                    true,
                    nonNegative(notes),
                    nonNegative(tasks),
                    nonNegative(tags),
                    nonNegative(skipped),
                    null);
        }
        return new HandoffResult(false, 0, 0, 0, 0, Reason.fromWire(reason));
    }

    private static int nonNegative(@Nullable Integer value) {
        return value == null || value < 0 ? 0 : value;
    }
}
