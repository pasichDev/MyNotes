package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The one SHA-256 the sync code hashes with.
 *
 * <p>Five copies of the digest-and-hex loop used to live in the store, the validator, the service
 * and the backend, three of them formatting through {@code String.format("%02x")} with the default
 * locale. A blob hashed by one copy has to match a manifest written by another, so the hex encoding
 * is a lookup table here and nowhere else.
 */
final class Sha256 {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Sha256() {}

    /** Lowercase hex, locale-independent by construction. */
    @NonNull
    static String hex(@NonNull byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            out[index * 2] = HEX[value >>> 4];
            out[index * 2 + 1] = HEX[value & 0xf];
        }
        return new String(out);
    }

    @NonNull
    static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            // Mandatory on every Java platform; treating it as recoverable only hides a broken
            // runtime behind a sync error.
            throw new IllegalStateException("SHA-256 is unavailable", missing);
        }
    }

    @NonNull
    static String of(@NonNull byte[] bytes) {
        return hex(newDigest().digest(bytes));
    }

    @NonNull
    static String of(@NonNull String value) {
        return of(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Reads {@code input} to its end without closing it. */
    @NonNull
    static String of(@NonNull InputStream input) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }

    @NonNull
    static String of(@NonNull File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            return of(input);
        }
    }
}
