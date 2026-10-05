package com.pasich.mynotes.utils.encly;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Decides whether the installed Encly may be handed every note in plain text.
 *
 * <p>The package name alone proves nothing — anything can be installed as {@code com.pasich.encly}
 * from outside a store — so the signing certificate is pinned. The package manager calls sit behind
 * {@link Inspector}, which keeps the decision itself, including the pre-API 28 path, under JVM
 * tests.
 */
public final class EnclyReceiverCheck {

    /** First API level with {@code PackageManager.hasSigningCertificate}. */
    static final int API_HAS_SIGNING_CERTIFICATE = 28;

    public enum Status {
        /** Encly is not installed: offer to get it. */
        NOT_INSTALLED,
        /** Encly is installed but too old to receive a hand-off: ask for an update. */
        NEEDS_UPDATE,
        /** Something named Encly is installed but not signed by Encly's key. Never send to it. */
        UNTRUSTED,
        /** The official Encly is installed and can receive. */
        READY
    }

    /** The package manager, reduced to what the decision needs. */
    public interface Inspector {
        int sdkInt();

        boolean isInstalled(@NonNull String packageName);

        /**
         * The fully qualified class name of the exported activity in {@code packageName} that
         * handles {@code action}, or {@code null} when none does.
         */
        @Nullable
        String findActivity(@NonNull String packageName, @NonNull String action);

        /** API 28+: {@code PackageManager.hasSigningCertificate(..., CERT_INPUT_SHA256)}. */
        boolean hasSigningCertificateSha256(@NonNull String packageName, @NonNull byte[] sha256);

        /**
         * API 26–27: the encoded certificates from {@code GET_SIGNATURES}, or {@code null} when the
         * package cannot be read.
         */
        @Nullable
        List<byte[]> legacySignatures(@NonNull String packageName);
    }

    public static final class Result {
        @NonNull public final Status status;

        /** The activity to start explicitly; set only when {@link #status} is READY. */
        @Nullable public final String activityClassName;

        Result(@NonNull Status status, @Nullable String activityClassName) {
            this.status = status;
            this.activityClassName = activityClassName;
        }
    }

    private EnclyReceiverCheck() {}

    @NonNull
    public static Result check(@NonNull Inspector inspector) {
        return check(inspector, EnclyHandoff.ENCLY_CERT_SHA256);
    }

    /** {@link #check(Inspector)} against any pin, so tests can sign with a certificate they own. */
    @NonNull
    static Result check(@NonNull Inspector inspector, @NonNull String pinnedSha256Hex) {
        String pkg = EnclyHandoff.ENCLY_PACKAGE;
        if (!inspector.isInstalled(pkg)) {
            return new Result(Status.NOT_INSTALLED, null);
        }
        if (!isSignedBy(inspector, pkg, hexToBytes(pinnedSha256Hex))) {
            return new Result(Status.UNTRUSTED, null);
        }
        String activity = inspector.findActivity(pkg, EnclyHandoff.ACTION_IMPORT);
        if (activity == null || activity.isEmpty()) {
            return new Result(Status.NEEDS_UPDATE, null);
        }
        return new Result(Status.READY, activity);
    }

    static boolean isSignedBy(
            @NonNull Inspector inspector, @NonNull String pkg, @NonNull byte[] pinned) {
        if (inspector.sdkInt() >= API_HAS_SIGNING_CERTIFICATE) {
            return inspector.hasSigningCertificateSha256(pkg, pinned);
        }
        // GET_SIGNATURES on API < 28 returns every signer, and is only safe to trust when there is
        // exactly one: with several, an attacker's certificate could sit beside the real one.
        List<byte[]> signatures = inspector.legacySignatures(pkg);
        if (signatures == null || signatures.size() != 1 || signatures.get(0) == null) {
            return false;
        }
        return MessageDigest.isEqual(pinned, sha256(signatures.get(0)));
    }

    @NonNull
    static byte[] sha256(@NonNull byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @NonNull
    static byte[] hexToBytes(@NonNull String hex) {
        if (hex.length() % 2 != 0) throw new IllegalArgumentException("odd hex length");
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
