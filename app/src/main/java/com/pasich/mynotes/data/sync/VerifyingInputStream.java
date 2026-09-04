package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Checks a content-addressed blob against its declared hash and size as the bytes go past.
 *
 * <p>This is the single verifier for every attachment transfer: local file to Drive, Drive to the
 * local cache, cache to a note folder, and the Drive-to-Drive copy into the canonical root. Four
 * separate implementations used to exist with four slightly different rules — one enforced the size
 * ceiling, one did not, one returned false where the others threw — so a blob accepted on one path
 * could be rejected on the next and leave a bundle pointing at bytes no device would serve.
 *
 * <p>Verification happens at end of stream, which is reached inside the destination's own read
 * loop, so a destination that streams straight to its final location still learns about a mismatch
 * before it commits. {@link #verifyEndOfStream()} drains whatever the destination left unread, so a
 * consumer that stopped early cannot accept a blob it never finished checking.
 */
final class VerifyingInputStream extends FilterInputStream {

    private final MessageDigest digest = Sha256.newDigest();
    private final String expectedHash;
    private final Long expectedSize;
    private final long maxBytes;
    private long byteCount;
    private boolean verified;
    private AttachmentIntegrityException integrityFailure;

    VerifyingInputStream(
            @NonNull InputStream input, @NonNull String expectedHash, @Nullable Long expectedSize) {
        this(input, expectedHash, expectedSize, SyncBundleValidator.MAX_ATTACHMENT_BYTES);
    }

    VerifyingInputStream(
            @NonNull InputStream input,
            @NonNull String expectedHash,
            @Nullable Long expectedSize,
            long maxBytes) {
        super(input);
        this.expectedHash = expectedHash;
        this.expectedSize = expectedSize;
        this.maxBytes = maxBytes;
    }

    /**
     * Reads {@code input} to its end and verifies it; the caller closes the stream.
     *
     * @return the blob's size in bytes.
     */
    static long verify(
            @NonNull InputStream input, @NonNull String expectedHash, @Nullable Long expectedSize)
            throws IOException {
        VerifyingInputStream verifying =
                new VerifyingInputStream(input, expectedHash, expectedSize);
        verifying.verifyEndOfStream();
        return verifying.bytesRead();
    }

    /** Bytes seen so far; the blob's size once {@link #verifyEndOfStream()} has passed. */
    long bytesRead() {
        return byteCount;
    }

    @Override
    public int read() throws IOException {
        rethrowIntegrityFailure();
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
        rethrowIntegrityFailure();
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

    private void enforceSizeLimit() throws AttachmentIntegrityException {
        if (byteCount > maxBytes) {
            failIntegrity("Attachment exceeds the sync size limit");
        }
    }

    /**
     * Checks the digest, draining anything the destination left behind first.
     *
     * <p>Idempotent: the destination's read loop reaches end of stream and verifies, and the caller
     * verifies again after the destination returns; the second call is a no-op.
     */
    void verifyEndOfStream() throws IOException {
        if (verified) {
            return;
        }
        rethrowIntegrityFailure();
        drainRemaining();
        String actualHash = Sha256.hex(digest.digest());
        if (!expectedHash.equals(actualHash)) {
            failIntegrity("Attachment checksum does not match its declared hash");
        }
        if (expectedSize != null && expectedSize.longValue() != byteCount) {
            failIntegrity("Attachment size does not match its declared size");
        }
        verified = true;
    }

    private void failIntegrity(String message) throws AttachmentIntegrityException {
        AttachmentIntegrityException failure = new AttachmentIntegrityException(message);
        integrityFailure = failure;
        throw failure;
    }

    private void rethrowIntegrityFailure() throws AttachmentIntegrityException {
        if (integrityFailure != null) {
            throw integrityFailure;
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
}
