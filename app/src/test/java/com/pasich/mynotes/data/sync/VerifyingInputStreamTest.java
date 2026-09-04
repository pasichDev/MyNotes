package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * The single verifier every attachment transfer goes through.
 *
 * <p>Four implementations used to disagree on the details pinned here — one had no size ceiling,
 * one returned false where the others threw — so a blob accepted on one path could be refused on
 * the next.
 */
public class VerifyingInputStreamTest {

    private static final byte[] BYTES = "attachment bytes".getBytes(StandardCharsets.UTF_8);
    private static final String HASH = Sha256.of(BYTES);

    @Test
    public void acceptsTheDeclaredBytesAndReportsTheirSize() throws Exception {
        long size =
                VerifyingInputStream.verify(
                        new ByteArrayInputStream(BYTES), HASH, (long) BYTES.length);

        assertThat(size).isEqualTo(BYTES.length);
    }

    @Test
    public void rejectsAChecksumMismatch() {
        AttachmentIntegrityException failure =
                expectIntegrityFailure(new ByteArrayInputStream(BYTES), Sha256.of("other"), null);

        assertThat(failure).hasMessageThat().contains("checksum");
    }

    @Test
    public void rejectsASizeMismatch() {
        AttachmentIntegrityException failure =
                expectIntegrityFailure(new ByteArrayInputStream(BYTES), HASH, 3L);

        assertThat(failure).hasMessageThat().contains("size does not match");
    }

    @Test
    public void rejectsABlobPastTheCeilingAsAnIntegrityFailure() throws Exception {
        // A plain IOException here escaped the "skip a corrupt candidate" path in the Drive
        // backend and failed the whole sync on one oversized object.
        VerifyingInputStream verifying =
                new VerifyingInputStream(new ByteArrayInputStream(BYTES), HASH, null, 4L);
        try {
            verifying.verifyEndOfStream();
            throw new AssertionError("Expected the ceiling to be enforced");
        } catch (AttachmentIntegrityException expected) {
            assertThat(expected).hasMessageThat().contains("exceeds the sync size limit");
        }
    }

    @Test
    public void drainsWhatTheDestinationLeftUnreadBeforeJudging() throws Exception {
        // A destination that stopped early must not be able to accept a blob it never finished
        // checking: the tail is read here and the mismatch surfaces.
        byte[] longer = "attachment bytes plus more".getBytes(StandardCharsets.UTF_8);
        VerifyingInputStream verifying =
                new VerifyingInputStream(new ByteArrayInputStream(longer), HASH, null);
        byte[] buffer = new byte[BYTES.length];
        assertThat(verifying.read(buffer, 0, buffer.length)).isEqualTo(BYTES.length);

        try {
            verifying.verifyEndOfStream();
            throw new AssertionError("Expected the drained tail to fail the checksum");
        } catch (AttachmentIntegrityException expected) {
            assertThat(verifying.bytesRead()).isEqualTo(longer.length);
        }
    }

    @Test
    public void verifyingTwiceIsANoOp() throws Exception {
        VerifyingInputStream verifying =
                new VerifyingInputStream(
                        new ByteArrayInputStream(BYTES), HASH, (long) BYTES.length);
        while (verifying.read(new byte[8]) != -1) {
            // Reaches end of stream, which verifies once inside the read.
        }

        verifying.verifyEndOfStream();
        verifying.verifyEndOfStream();
    }

    private static AttachmentIntegrityException expectIntegrityFailure(
            ByteArrayInputStream input, String hash, Long size) {
        try {
            VerifyingInputStream.verify(input, hash, size);
        } catch (AttachmentIntegrityException expected) {
            return expected;
        } catch (IOException unexpected) {
            throw new AssertionError(unexpected);
        }
        throw new AssertionError("Expected an integrity failure");
    }
}
