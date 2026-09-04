package com.pasich.mynotes.data.sync;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Coordinates one manual sync without depending on a cloud provider or Room.
 *
 * <p>The order is intentional: merge first, make all immutable attachment blobs available at both
 * endpoints, publish the remote snapshot, then apply the local snapshot transaction. Therefore a
 * failed upload can leave harmless orphaned blobs, but it can never publish records that reference
 * unavailable attachments or partially apply remote records locally.
 */
public final class SyncService {

    private static final String TAG = "SyncService";
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final long MAX_TOLERATED_CLOCK_SKEW_MILLIS = 24L * 60L * 60L * 1000L;

    private final SyncStore store;
    private final SyncMerger merger;
    private final Clock clock;

    public SyncService(@NonNull SyncStore store) {
        this(store, new SyncMerger(), Clock.systemUTC());
    }

    public SyncService(@NonNull SyncStore store, @NonNull SyncMerger merger, @NonNull Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.merger = Objects.requireNonNull(merger, "merger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Serializes every sync attempt in the process.
     *
     * <p>This method used to rely on {@code synchronized}, but a fresh {@code SyncService} is
     * constructed for each attempt — once by the Backup screen and once by {@code
     * GoogleDriveSyncWorker} — so the monitor was per-instance and guarded nothing. A manual sync
     * and the six-hourly worker could interleave their Room writes and both publish a bundle.
     */
    private static final ReentrantLock SYNC_LOCK = new ReentrantLock();

    private static final long LOCK_WAIT_SECONDS = 5L;

    /** Runs one serialized manual synchronization attempt and returns its durable final state. */
    @NonNull
    public SyncState sync(@NonNull SyncBackend backend) {
        boolean acquired = false;
        try {
            acquired = SYNC_LOCK.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (!acquired) {
            // Deliberately not persisted: the sync that holds the lock owns the stored state.
            // The wording keeps GoogleDriveSyncWorker.isRetryable() treating this as retryable.
            return SyncState.error(
                    "google-drive",
                    safeReadState().getLastSuccessfulSyncAt(),
                    "Another sync is already running; this attempt was temporarily skipped");
        }
        try {
            return syncExclusively(backend);
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    @NonNull
    private SyncState syncExclusively(@NonNull SyncBackend backend) {
        SyncState previousState = safeReadState();
        String backendIdentifier = "unknown";

        try {
            Objects.requireNonNull(backend, "backend");
            backendIdentifier = requireBackendIdentifier(backend.getIdentifier());
            Instant startedAt = clock.instant();
            persistState(
                    SyncState.syncing(
                            backendIdentifier, startedAt, previousState.getLastSuccessfulSyncAt()));
            SnapshotBuildResult localBuild =
                    Objects.requireNonNull(store.buildSnapshot(), "local snapshot build");
            // Do this before reading Drive or transferring blobs. Publishing a snapshot that
            // merely skipped an unresolved local attachment turns a local storage fault into
            // permanent remote data loss on the next successful sync from another device.
            SyncSnapshot local = localBuild.requireSnapshot();
            SyncSnapshot remote = Objects.requireNonNull(backend.readSnapshot(), "remote snapshot");
            warnAboutClockSkew(remote);
            SyncMergeResult mergeResult = merger.merge(local, remote);
            SyncSnapshot merged = mergeResult.getMergedSnapshot();

            Map<String, Long> expectedSizes = attachmentSizes(merged);
            synchronizeAttachments(backend, merged, expectedSizes);
            if (!snapshotsMatch(merged, remote)) {
                backend.writeSnapshot(merged);
            }
            SyncState success =
                    SyncState.success(
                            backendIdentifier, clock.instant(), mergeResult.getConflicts().size());
            store.applySnapshot(merged, mergeResult.getConflicts(), success);
            return success;
        } catch (Exception exception) {
            SyncState failure =
                    SyncState.error(
                            backendIdentifier,
                            previousState.getLastSuccessfulSyncAt(),
                            safeErrorMessage(exception));
            persistState(failure);
            return failure;
        }
    }

    /**
     * Flags a device clock that disagrees badly with the rest of the account.
     *
     * <p>Merging is last-write-wins on wall-clock time. Per record that self-corrects: {@code
     * SyncMetadataDao.touch} assigns {@code max(now, storedUpdatedAt + 1)}, so once a device has
     * seen a newer remote version its own next edit outranks it however far behind its clock runs.
     * What stays exposed is the first divergent edit to a record neither side has synced since,
     * where the raw clocks decide and the slower device loses silently. A wrong device clock is
     * therefore worth a line in the log when support has to explain a "lost" edit.
     */
    private void warnAboutClockSkew(@NonNull SyncSnapshot remote) {
        Instant newest = null;
        for (SyncRecord record : remote.getRecords()) {
            if (newest == null || record.getUpdatedAt().isAfter(newest)) {
                newest = record.getUpdatedAt();
            }
        }
        if (newest == null) {
            return;
        }
        long skewMillis = newest.toEpochMilli() - clock.millis();
        if (skewMillis > MAX_TOLERATED_CLOCK_SKEW_MILLIS) {
            Log.w(
                    TAG,
                    "Remote records are "
                            + (skewMillis / 3_600_000L)
                            + "h ahead of this device's clock; merge order may be wrong");
        }
    }

    private static boolean snapshotsMatch(
            @NonNull SyncSnapshot first, @NonNull SyncSnapshot second) {
        Collection<SyncRecord> firstRecords = first.getRecords();
        Collection<SyncRecord> secondRecords = second.getRecords();
        if (firstRecords.size() != secondRecords.size()) {
            return false;
        }
        java.util.Iterator<SyncRecord> firstIterator = firstRecords.iterator();
        java.util.Iterator<SyncRecord> secondIterator = secondRecords.iterator();
        while (firstIterator.hasNext()) {
            SyncRecord firstRecord = firstIterator.next();
            SyncRecord secondRecord = secondIterator.next();
            if (firstRecord.getType() != secondRecord.getType()
                    || !firstRecord.getId().equals(secondRecord.getId())
                    || !firstRecord
                            .getCanonicalPayloadHash()
                            .equals(secondRecord.getCanonicalPayloadHash())) {
                return false;
            }
        }
        return true;
    }

    private void synchronizeAttachments(
            SyncBackend backend, SyncSnapshot merged, Map<String, Long> expectedSizes)
            throws IOException {
        Collection<String> hashes =
                Objects.requireNonNull(store.getAttachmentHashes(merged), "hashes");
        for (String hash : hashes) {
            validateHash(hash);
            if (store.hasAttachment(hash)) {
                if (backend.hasAttachment(hash)) {
                    try {
                        verifyAttachment(hash, expectedSizes.get(hash), store.readAttachment(hash));
                    } catch (IOException localError) {
                        // The local copy is missing or corrupt; repair it from the remote blob.
                        copyVerified(
                                hash,
                                expectedSizes.get(hash),
                                backend.readAttachment(hash),
                                store::writeAttachment);
                    }
                    // Drive is untrusted. A matching appProperty is only a claim, so verify the
                    // actual remote bytes before a bundle can make that blob durable state.
                    verifyAttachment(hash, expectedSizes.get(hash), backend.readAttachment(hash));
                } else {
                    copyVerified(
                            hash,
                            expectedSizes.get(hash),
                            store.readAttachment(hash),
                            backend::writeAttachment);
                }
            } else {
                InputStream remoteAttachment = backend.readAttachment(hash);
                if (remoteAttachment == null) {
                    throw new IOException("Required attachment is unavailable: " + hash);
                }
                copyVerified(
                        hash, expectedSizes.get(hash), remoteAttachment, store::writeAttachment);
            }
        }
    }

    private void verifyAttachment(String hash, Long expectedSize, InputStream source)
            throws IOException {
        if (source == null) {
            throw new IOException("Required attachment is unavailable: " + hash);
        }
        try (InputStream input = source;
                VerifyingInputStream verified =
                        new VerifyingInputStream(input, hash, expectedSize)) {
            byte[] buffer = new byte[8192];
            while (verified.read(buffer) != -1) {
                // Consume the complete blob before accepting an existing remote attachment.
            }
            verified.verifyEndOfStream();
        }
    }

    /**
     * Pipes one blob to the other endpoint, verifying it as the bytes go past.
     *
     * <p>This used to buffer the whole blob into a {@code ByteArrayOutputStream}, call {@code
     * toByteArray()} and hand the destination a {@code ByteArrayInputStream}. With the 100 MB
     * attachment ceiling that peaked at several hundred megabytes of heap for a single file — an
     * {@code OutOfMemoryError} on any ordinary phone. Nothing is buffered now: {@link
     * VerifyingInputStream} checks the digest at end of stream, which happens inside the
     * destination's own read loop, so a corrupt blob still aborts the write before it is committed.
     */
    private void copyVerified(
            String expectedHash,
            Long expectedSize,
            InputStream source,
            AttachmentWriter destination)
            throws IOException {
        Objects.requireNonNull(source, "source");
        try (InputStream input = source;
                VerifyingInputStream verified =
                        new VerifyingInputStream(input, expectedHash, expectedSize)) {
            destination.write(expectedHash, expectedSize == null ? -1L : expectedSize, verified);
            verified.verifyEndOfStream();
        }
    }

    private static Map<String, Long> attachmentSizes(SyncSnapshot snapshot) throws IOException {
        Map<String, Long> sizes = new HashMap<>();
        for (SyncRecord record : snapshot.getLiveRecords(SyncRecord.Type.NOTE)) {
            JsonArray manifest = record.getPayload().getAsJsonArray("attachmentsManifest");
            if (manifest == null) continue;
            for (JsonElement element : manifest) {
                if (!element.isJsonObject()) continue;
                JsonObject value = element.getAsJsonObject();
                if (!value.has("sha256") || !value.has("size")) continue;
                String hash = value.get("sha256").getAsString();
                long size = value.get("size").getAsLong();
                if (size < 0L || size > SyncBundleValidator.MAX_ATTACHMENT_BYTES) {
                    throw new IOException("Attachment size exceeds the sync limit");
                }
                Long previous = sizes.putIfAbsent(hash, size);
                if (previous != null && previous.longValue() != size) {
                    throw new IOException("Attachment metadata has conflicting sizes");
                }
            }
        }
        return sizes;
    }

    private SyncState safeReadState() {
        try {
            SyncState state = store.readState();
            return state == null ? SyncState.idle() : state;
        } catch (Exception ignored) {
            return SyncState.idle();
        }
    }

    private void persistState(SyncState state) {
        try {
            store.writeState(state);
        } catch (Exception ignored) {
            // Content sync remains valid when only the user-visible status store is temporarily
            // unavailable.
        }
    }

    @NonNull
    private static String requireBackendIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Sync backend identifier must not be empty");
        }
        return identifier.trim();
    }

    @NonNull
    private static String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName()
                : message.trim();
    }

    private static void validateHash(String hash) throws IOException {
        if (hash == null || !SHA_256.matcher(hash).matches()) {
            throw new IOException("Attachment hash must be a lowercase SHA-256 value");
        }
    }

    private interface AttachmentWriter {
        void write(String hash, long sizeBytes, InputStream content) throws IOException;
    }

    /** Verifies the hash only after the receiving endpoint consumed every byte. */
    private static final class VerifyingInputStream extends FilterInputStream {
        private final MessageDigest digest;
        private final String expectedHash;
        private final Long expectedSize;
        private long byteCount;
        private boolean verified;

        VerifyingInputStream(InputStream input, String expectedHash, Long expectedSize) {
            super(input);
            this.expectedHash = expectedHash;
            this.expectedSize = expectedSize;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                digest.update((byte) value);
                byteCount++;
                enforceSizeLimit();
            } else {
                verifyEndOfStream();
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                digest.update(buffer, offset, read);
                byteCount += read;
                enforceSizeLimit();
            } else if (read < 0) {
                verifyEndOfStream();
            }
            return read;
        }

        private void enforceSizeLimit() throws IOException {
            if (byteCount > SyncBundleValidator.MAX_ATTACHMENT_BYTES) {
                throw new IOException("Attachment exceeds the sync size limit");
            }
        }

        /**
         * Checks the digest, draining anything the destination left behind first.
         *
         * <p>Reached from {@link #read} at end of stream, so a destination that streams straight to
         * its final location still learns about a mismatch before it commits.
         */
        void verifyEndOfStream() throws IOException {
            if (verified) {
                return;
            }
            // Set before draining: drainRemaining reads through super, but a caller reaching this
            // from read() must not be able to re-enter.
            verified = true;
            drainRemaining();
            String actualHash = toHex(digest.digest());
            if (!expectedHash.equals(actualHash)) {
                throw new IOException("Attachment checksum does not match its declared hash");
            }
            if (expectedSize != null && expectedSize.longValue() != byteCount) {
                throw new IOException("Attachment size does not match its declared size");
            }
        }

        /** Reads through {@code super} so the digest covers bytes the destination skipped. */
        private void drainRemaining() throws IOException {
            byte[] scratch = new byte[8192];
            int read;
            while ((read = super.read(scratch, 0, scratch.length)) != -1) {
                digest.update(scratch, 0, read);
                byteCount += read;
                enforceSizeLimit();
            }
        }

        @NonNull
        private static String toHex(byte[] bytes) {
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        }
    }
}
