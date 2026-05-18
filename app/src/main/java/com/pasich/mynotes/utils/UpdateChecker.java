package com.pasich.mynotes.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import com.pasich.mynotes.cache.AppPreferencesCache;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Class for checking app updates */
@Singleton
public class UpdateChecker {

    private final Context context;

    private final AppPreferencesCache cache;

    @Inject
    public UpdateChecker(@ApplicationContext Context context, AppPreferencesCache cache) {
        this.context = context;
        this.cache = cache;
        this.cache.initialize();
    }

    /**
     * Check if a new version of the app is available
     *
     * @return true if there is a new version
     */
    public boolean hasNewVersion() {
        String currentVersion = getCurrentAppVersion();
        String lastKnownVersion = cache.getLastKnownVersion();

        // If lastKnownVersion is empty - this is the first launch
        if (lastKnownVersion == null || lastKnownVersion.isEmpty()) {
            return false;
        }

        return !currentVersion.equals(lastKnownVersion);
    }

    /**
     * Get the current app version
     *
     * @return App version
     */
    public String getCurrentAppVersion() {
        try {
            PackageInfo packageInfo =
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0.0";
        }
    }

    /** Mark the current version as read/acknowledged by the user */
    public void markVersionAsRead() {
        String currentVersion = getCurrentAppVersion();
        cache.setLastKnownVersion(currentVersion);
    }

    /** Initialize version check (should be called on the first launch) */
    public void initializeVersionCheck() {
        String lastKnownVersion = cache.getLastKnownVersion();
        if (lastKnownVersion == null || lastKnownVersion.isEmpty()) {
            // First launch - save the current version
            markVersionAsRead();
        }
    }
}
