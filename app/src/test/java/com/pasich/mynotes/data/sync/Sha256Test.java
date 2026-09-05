package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The one digest every sync component hashes with.
 *
 * <p>A blob hashed by the store has to match a manifest written by the validator and a checksum
 * compared by the backend, so the encoding is pinned rather than left to whichever of the former
 * five copies a caller happened to reach.
 */
public class Sha256Test {

    private static final String EMPTY =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void everyOverloadAgreesOnTheKnownVector() throws Exception {
        File file = temporaryFolder.newFile("empty");
        Files.write(file.toPath(), new byte[0]);

        assertThat(Sha256.of(new byte[0])).isEqualTo(EMPTY);
        assertThat(Sha256.of("")).isEqualTo(EMPTY);
        assertThat(Sha256.of(new ByteArrayInputStream(new byte[0]))).isEqualTo(EMPTY);
        assertThat(Sha256.of(file)).isEqualTo(EMPTY);
        assertThat(Sha256.of("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    public void hexIsLowercaseAndZeroPadded() {
        assertThat(Sha256.hex(new byte[] {0, (byte) 0xff, 0x10, 0x0a})).isEqualTo("00ff100a");
    }

    @Test
    public void hexDoesNotDependOnTheDefaultLocale() {
        // Three of the former copies formatted through String.format("%02x") with the default
        // locale; a locale with its own digits would have made a locally hashed blob mismatch the
        // manifest it was validated against.
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));
            assertThat(Sha256.of("")).isEqualTo(EMPTY);
        } finally {
            Locale.setDefault(previous);
        }
    }
}
