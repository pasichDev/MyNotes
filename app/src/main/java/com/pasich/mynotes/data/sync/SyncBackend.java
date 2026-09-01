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
 * {@link #writeSnapshot(SyncSnapshot)} is the commit point: implementations must not make a new
 * manifest visible before the call succeeds. The service uploads every required attachment before
 * it calls this method.
 */
public interface SyncBackend {

    /** A stable, non-secret identifier persisted with the last sync state. */
    @NonNull
    String getIdentifier();

    /**
     * Reads the current remote manifest.
     *
     * <p>Returns {@link SyncSnapshot#empty()} when this backend has not yet received a sync bundle.
     */
    @NonNull
    SyncSnapshot readSnapshot() throws IOException;

    /** Publishes a complete remote snapshot. Implementations must not expose a partial snapshot. */
    void writeSnapshot(@NonNull SyncSnapshot snapshot) throws IOException;

    /** Returns true when the immutable attachment blob already exists remotely. */
    boolean hasAttachment(@NonNull String sha256) throws IOException;

    /**
     * Opens an attachment by its lowercase SHA-256 hash, or returns {@code null} when it is absent.
     * The caller closes the returned stream.
     */
    @Nullable
    InputStream readAttachment(@NonNull String sha256) throws IOException;

    /**
     * Stores a complete attachment under {@code sha256}.
     *
     * <p>The implementation must consume the stream before returning and must not expose a partial
     * file after an exception.
     */
    void writeAttachment(@NonNull String sha256, @NonNull InputStream content) throws IOException;
}
