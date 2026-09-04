package com.pasich.mynotes.data.sync;

/**
 * What the user chose for one conflict.
 *
 * <p>{@link #KEEP_WINNER} and {@link #KEEP_ALTERNATIVE} address the two versions by their place in
 * the conflict rather than by where they came from. The older {@link #KEEP_LOCAL} and {@link
 * #KEEP_DRIVE} assumed every conflict had exactly one local and one remote side, which is false for
 * a conflict between two Drive bundle heads: whichever version happened to be the merge accumulator
 * was labelled local, so "keep my device's version" applied something that had never been on the
 * device. They are retained only so already-resolved rows still render.
 */
public enum SyncResolution {
    PENDING,
    /** Keep the version the deterministic merge selected. */
    KEEP_WINNER,
    /** Keep the other version the merge set aside. */
    KEEP_ALTERNATIVE,
    /**
     * @deprecated provenance-sensitive; kept for reading historical rows.
     */
    @Deprecated
    KEEP_LOCAL,
    /**
     * @deprecated provenance-sensitive; kept for reading historical rows.
     */
    @Deprecated
    KEEP_DRIVE;

    public static SyncResolution fromStoredValue(String value) {
        if (value == null || value.trim().isEmpty()) return PENDING;
        try {
            return SyncResolution.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return PENDING;
        }
    }

    /** True for a choice that names a version rather than an endpoint. */
    public boolean isVersionAddressed() {
        return this == KEEP_WINNER || this == KEEP_ALTERNATIVE;
    }
}
