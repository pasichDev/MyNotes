package com.pasich.mynotes.utils.auth;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Whether Google Play services can serve this device at all.
 *
 * <p>Every part of sync sits on Play services: Credential Manager signs in through it and the Drive
 * scope is authorized by it. Without it the sign-in button leads nowhere, so the account tab has to
 * know before it offers anything.
 *
 * <p>The usual answer is {@code GoogleApiAvailability}, which means linking play-services-base and
 * loading a Play services class to ask whether Play services exist — the one question that must
 * stay answerable when they do not. Reading the package table instead needs no dependency and
 * cannot fail for the reason it is checking.
 */
public final class PlayServicesAvailability {

    /** The package behind every Google Play services API. */
    public static final String PLAY_SERVICES_PACKAGE = "com.google.android.gms";

    /**
     * The single fact this class needs, behind a seam so every outcome is testable.
     *
     * <p>An Activity is not constructible in a plain JVM test and this project carries no
     * Robolectric, so the branches would otherwise only ever run on a device that has Play services
     * — which is exactly the case that does not need checking.
     */
    public interface Lookup {
        /**
         * @return {@code TRUE} when the package is installed and enabled, {@code FALSE} when it is
         *     installed but disabled, {@code null} when it is not installed.
         */
        @Nullable
        Boolean isPackageEnabled(@NonNull String packageName);
    }

    private PlayServicesAvailability() {
        // no instance
    }

    /** True only when Play services are installed and the user has not disabled them. */
    public static boolean isAvailable(@Nullable Context context) {
        return context != null && isAvailable(packageLookup(context));
    }

    static boolean isAvailable(@NonNull Lookup lookup) {
        try {
            return Boolean.TRUE.equals(lookup.isPackageEnabled(PLAY_SERVICES_PACKAGE));
        } catch (RuntimeException unanswerable) {
            // A dead package manager or a ROM that refuses the query must not take the app down;
            // an unanswerable question is answered "no", which only hides sync.
            return false;
        }
    }

    @NonNull
    static Lookup packageLookup(@NonNull Context context) {
        return packageName -> {
            PackageManager packages = context.getPackageManager();
            if (packages == null) {
                return null;
            }
            try {
                ApplicationInfo info = packages.getApplicationInfo(packageName, 0);
                return info.enabled;
            } catch (PackageManager.NameNotFoundException absent) {
                // Also how the package table answers when the manifest does not declare the
                // package in <queries>, which is why it does.
                return null;
            }
        };
    }
}
