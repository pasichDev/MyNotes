package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What to do with a preferences journal found at startup.
 *
 * <p>SharedPreferences is outside Room, so a snapshot apply writes its intent to a journal row
 * inside the Room transaction and clears it only after a durable commit. Any of those steps can be
 * interrupted, and the right response differs per case — replaying unconditionally would overwrite
 * settings the user has since changed by hand. The decision is pure, and separate from Room, so
 * every crash window is covered by ordinary unit tests rather than only on a device.
 */
public final class PendingPreferencesDecision {

    public enum Action {
        /** No journal row; nothing to do. */
        NOTHING,
        /** The payload cannot be read. Set it aside rather than fail sync forever. */
        QUARANTINE,
        /** The live values already match the target: the write landed, only the clear was lost. */
        CLEAR_ALREADY_APPLIED,
        /** The live values still match the baseline, so the payload is still the right answer. */
        REPLAY,
        /** The user changed these settings after the journal was written; their choice wins. */
        DISCARD_STALE
    }

    private PendingPreferencesDecision() {}

    /**
     * Decides the fate of one journal row.
     *
     * @param payloadReadable whether the stored payload parsed into usable settings.
     * @param targetHash digest the journal intends to produce; empty when unknown.
     * @param baselineHash digest of the live settings when the journal was written; empty when
     *     unknown, which is treated as "cannot prove staleness" and therefore replayable.
     * @param liveHash digest of the settings visible right now.
     */
    @NonNull
    public static Action decide(
            boolean rowPresent,
            boolean payloadReadable,
            @Nullable String targetHash,
            @Nullable String baselineHash,
            @NonNull String liveHash) {
        if (!rowPresent) {
            return Action.NOTHING;
        }
        if (!payloadReadable) {
            return Action.QUARANTINE;
        }
        if (targetHash != null && !targetHash.isEmpty() && targetHash.equals(liveHash)) {
            return Action.CLEAR_ALREADY_APPLIED;
        }
        if (baselineHash == null || baselineHash.isEmpty()) {
            // Written before the journal carried identity. Staleness cannot be proven, and the
            // journal only exists because a sync meant to apply it, so replay is the safe read.
            return Action.REPLAY;
        }
        return baselineHash.equals(liveHash) ? Action.REPLAY : Action.DISCARD_STALE;
    }
}
