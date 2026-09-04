package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Provider boundary for a sync destination.
 *
 * <p>Implementations may use Google Drive, WebDAV, or an on-device test store. Attachments are
 * addressed by their content SHA-256, so writing the same attachment more than once must be safe.
 * {@link #publish(SyncPublication)} is the commit point: implementations must not make a new
 * manifest visible before the call succeeds. The service uploads every required attachment before
 * it calls this method.
 *
 * <p>There is deliberately no plain "read a snapshot" or "write a snapshot" pair. A publish has to
 * quote the read it was derived from, or a backend that keeps causal history cannot tell a write
 * with stale parents from a legitimate one; the Drive backend had to disable exactly such a
 * default, and a future backend must not be able to inherit it by accident.
 */
public interface SyncBackend {

    /** A stable, non-secret identifier persisted with the last sync state. */
    @NonNull
    String getIdentifier();

    /**
     * Reads the remote causal frontier.
     *
     * <p>Returns an empty snapshot, with a read token the next publish can quote, when this backend
     * has not yet received a sync bundle.
     */
    @NonNull
    RemoteSnapshot readSnapshotResult() throws IOException;

    /**
     * Publishes a snapshot together with the unresolved conflict versions it must keep alive and
     * the read context it was derived from.
     *
     * <p>Implementations must refuse a publication whose read context is not their latest read.
     */
    void publish(@NonNull SyncPublication publication) throws IOException;

    /** Returns true when the immutable attachment blob already exists remotely. */
    boolean hasAttachment(@NonNull String sha256) throws IOException;

    /**
     * True only when the remote blob exists and its bytes really do hash to {@code sha256}.
     *
     * <p>Separate from {@link #hasAttachment} because a backend may index blobs by a claimed hash
     * that has to be checked against the bytes before a bundle can depend on it. Implementations
     * are expected to answer this at most once per blob per sync.
     */
    default boolean hasVerifiedAttachment(@NonNull String sha256, @Nullable Long expectedSize)
            throws IOException {
        InputStream content = readAttachment(sha256);
        if (content == null) {
            return false;
        }
        try (InputStream input = content) {
            VerifyingInputStream.verify(input, sha256, expectedSize);
            return true;
        } catch (AttachmentIntegrityException corrupt) {
            return false;
        }
    }

    /**
     * Opens an attachment by its lowercase SHA-256 hash, or returns {@code null} when it is absent.
     * The caller closes the returned stream and is responsible for verifying it.
     */
    @Nullable
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
}
