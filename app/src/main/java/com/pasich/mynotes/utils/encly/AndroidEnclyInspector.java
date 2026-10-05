package com.pasich.mynotes.utils.encly;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** {@link EnclyReceiverCheck.Inspector} backed by the real package manager. */
public final class AndroidEnclyInspector implements EnclyReceiverCheck.Inspector {

    private final PackageManager packageManager;
    private final Uri probeUri;

    /**
     * @param probeUri a URI of the same shape as the real hand-off file, so the lookup matches
     *     Encly's intent filter exactly as the launch will.
     */
    public AndroidEnclyInspector(@NonNull Context context, @NonNull Uri probeUri) {
        this.packageManager = context.getPackageManager();
        this.probeUri = probeUri;
    }

    @Override
    public int sdkInt() {
        return Build.VERSION.SDK_INT;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isInstalled(@NonNull String packageName) {
        try {
            packageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Nullable
    @Override
    public String findActivity(@NonNull String packageName, @NonNull String action) {
        Intent withData = new Intent(action).setPackage(packageName);
        withData.setDataAndType(probeUri, EnclyHandoff.MIME_TYPE);
        String found = firstExported(withData, packageName);
        if (found != null) return found;
        // A filter that declares only the action does not match an intent carrying a type; the
        // launch names the component explicitly, so the action alone is enough to reach it.
        return firstExported(new Intent(action).setPackage(packageName), packageName);
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private String firstExported(@NonNull Intent intent, @NonNull String packageName) {
        List<ResolveInfo> matches = packageManager.queryIntentActivities(intent, 0);
        if (matches == null) return null;
        for (ResolveInfo match : matches) {
            ActivityInfo info = match.activityInfo;
            if (info != null && info.exported && packageName.equals(info.packageName)) {
                return info.name;
            }
        }
        return null;
    }

    @Override
    public boolean hasSigningCertificateSha256(
            @NonNull String packageName, @NonNull byte[] sha256) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false;
        return packageManager.hasSigningCertificate(
                packageName, sha256, PackageManager.CERT_INPUT_SHA256);
    }

    /**
     * Only reached below API 28. {@code GET_SIGNATURES} is unsafe when it is trusted with several
     * signers; {@link EnclyReceiverCheck} accepts exactly one.
     */
    @Nullable
    @Override
    @SuppressLint("PackageManagerGetSignatures")
    @SuppressWarnings("deprecation")
    public List<byte[]> legacySignatures(@NonNull String packageName) {
        try {
            PackageInfo info =
                    packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            if (info.signatures == null) return null;
            List<byte[]> out = new ArrayList<>(info.signatures.length);
            for (Signature signature : info.signatures) {
                out.add(signature == null ? null : signature.toByteArray());
            }
            return out;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
