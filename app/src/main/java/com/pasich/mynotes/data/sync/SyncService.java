package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

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

    /** Runs one serialized manual synchronization attempt and returns its durable final state. */
    @NonNull
    public synchronized SyncState sync(@NonNull SyncBackend backend) {
        Objects.requireNonNull(backend, "backend");
        String backendIdentifier = requireBackendIdentifier(backend.getIdentifier());
        SyncState previousState = safeReadState();
        Instant startedAt = clock.instant();
        persistState(SyncState.syncing(backendIdentifier, startedAt));

        try {
            SyncSnapshot local = Objects.requireNonNull(store.readSnapshot(), "local snapshot");
            SyncSnapshot remote = Objects.requireNonNull(backend.readSnapshot(), "remote snapshot");
            SyncMergeResult mergeResult = merger.merge(local, remote);
            SyncSnapshot merged = mergeResult.getMergedSnapshot();

            synchronizeAttachments(backend, merged, attachmentSizes(merged));
            backend.writeSnapshot(merged);
            store.applySnapshot(merged, mergeResult.getConflicts());

            SyncState success =
                    SyncState.success(
                            backendIdentifier, clock.instant(), mergeResult.getConflicts().size());
            persistState(success);
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

    private void synchronizeAttachments(
            SyncBackend backend, SyncSnapshot merged, Map<String, Long> expectedSizes)
            throws IOException {
        Collection<String> hashes =
                Objects.requireNonNull(store.getAttachmentHashes(merged), "hashes");
        for (String hash : hashes) {
            validateHash(hash);
            if (store.hasAttachment(hash)) {
                if (backend.hasAttachment(hash)) {
                    verifyRemoteAttachment(
                            hash, expectedSizes.get(hash), backend.readAttachment(hash));
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

    private void verifyRemoteAttachment(String hash, Long expectedSize, InputStream source)
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

    private void copyVerified(
            String expectedHash,
            Long expectedSize,
            InputStream source,
            AttachmentWriter destination)
            throws IOException {
        Objects.requireNonNull(source, "source");
        byte[] verifiedBytes;
        try (InputStream input = source;
                VerifyingInputStream verified =
                        new VerifyingInputStream(input, expectedHash, expectedSize);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = verified.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            verified.verifyEndOfStream();
            verifiedBytes = output.toByteArray();
        }
        destination.write(expectedHash, new ByteArrayInputStream(verifiedBytes));
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
        } catch (IOException ignored) {
            return SyncState.idle();
        }
    }

    private void persistState(SyncState state) {
        try {
            store.writeState(state);
        } catch (IOException ignored) {
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
        void write(String hash, InputStream content) throws IOException;
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
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                digest.update(buffer, offset, read);
                byteCount += read;
            }
            return read;
        }

        void verifyEndOfStream() throws IOException {
            if (!verified) {
                while (read(new byte[8192]) != -1) {
                    // Drain an incorrectly implemented destination before declaring success.
                }
                String actualHash = toHex(digest.digest());
                if (!expectedHash.equals(actualHash)) {
                    throw new IOException("Attachment checksum does not match its declared hash");
                }
                if (expectedSize != null && expectedSize.longValue() != byteCount) {
                    throw new IOException("Attachment size does not match its declared size");
                }
                verified = true;
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
