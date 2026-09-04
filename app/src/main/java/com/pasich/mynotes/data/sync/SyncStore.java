package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

/**
 * Local persistence boundary used by {@link SyncService}.
 *
 * <p>A Room-backed implementation builds a snapshot from notes, tasks, tags and their {@code
 * SyncMetadataEntity} rows. {@link #applySnapshot(SyncSnapshot, List)} must apply the records,
 * tombstones and conflict report in one database transaction. Keeping that work behind this small
 * interface lets the merge protocol be unit tested without Android or Room.
 */
public interface SyncStore {

    /** Builds a consistent local snapshot, including retained tombstones. */
    @NonNull
    SyncSnapshot readSnapshot() throws IOException;

    /**
     * Builds a local snapshot together with any integrity problems that make publication unsafe.
     * Implementations that cannot identify such problems retain the legacy snapshot boundary.
     */
    @NonNull
    default SnapshotBuildResult buildSnapshot() throws IOException {
        return SnapshotBuildResult.publishable(readSnapshot());
    }

    /**
     * Applies the merged snapshot and conflict report atomically.
     *
     * <p>This is called only after every attachment required by the snapshot is available locally
     * and after the remote snapshot commit succeeds.
     */
    void applySnapshot(
            @NonNull SyncSnapshot snapshot, @NonNull List<SyncMergeResult.Conflict> conflicts)
            throws IOException;

    /**
     * Applies a successful sync result and persists its final state in the same transaction.
     * Implementations without a transactional state store retain compatibility by writing the state
     * immediately after applying the snapshot.
     */
    default void applySnapshot(
            @NonNull SyncSnapshot snapshot,
            @NonNull List<SyncMergeResult.Conflict> conflicts,
            @NonNull SyncState finalState)
            throws IOException {
        applySnapshot(snapshot, conflicts);
        writeState(finalState);
    }

    /**
     * Version identities the user has already settled, so they are never offered again.
     *
     * <p>Published with the bundle: a resolution has to retire an alternative on every device, not
     * only on the one where the user made the choice.
     */
    @NonNull
    default java.util.Set<String> getResolvedAlternativeIds() throws IOException {
        return java.util.Collections.emptySet();
    }

    /** Returns every attachment content hash referenced by {@code snapshot}. */
    @NonNull
    Collection<String> getAttachmentHashes(@NonNull SyncSnapshot snapshot) throws IOException;

    /** True when the complete attachment is locally available. */
    boolean hasAttachment(@NonNull String sha256) throws IOException;

    /**
     * Names the record a blob belongs to when the store published it without holding the bytes.
     *
     * <p>A store may describe an attachment from remembered metadata after its file has gone,
     * expecting the bytes to come back from the remote. When no endpoint has them either, the
     * failure has to name the note to fix, not a hash; a store that never does this answers null.
     */
    @Nullable
    default SnapshotProblem describeMissingAttachment(@NonNull String sha256) {
        return null;
    }

    /**
     * True when the blob already sits in the store's own durable cache, as written by {@link
     * #writeAttachment}, rather than only in a note's folder that a later edit may empty.
     *
     * <p>Lets the service pin a conflict version's blobs without copying a cached blob onto itself;
     * a store without such a cache answers false and is simply written to again.
     *
     * @param sizeBytes the declared size, or a negative value to accept any.
     */
    default boolean hasDurableAttachment(@NonNull String sha256, long sizeBytes)
            throws IOException {
        return false;
    }

    /** Opens one complete local attachment. The caller closes the returned stream. */
    @NonNull
    InputStream readAttachment(@NonNull String sha256) throws IOException;

    /**
     * Stores one immutable blob, streaming it rather than holding it in memory.
     *
     * <p>The implementation must consume the stream before returning and must not expose a partial
     * file after an exception.
     *
     * @param sizeBytes the blob's declared size, or a negative value when it is unknown. A known
     *     size lets an implementation avoid buffering the whole blob to compute a content length.
     */
    void writeAttachment(@NonNull String sha256, long sizeBytes, @NonNull InputStream content)
            throws IOException;

    /** Returns the last durable state, or {@link SyncState#idle()} before the first sync. */
    @NonNull
    SyncState readState() throws IOException;

    /** Persists a state transition that is not coupled to a content transaction. */
    void writeState(@NonNull SyncState state) throws IOException;
}
