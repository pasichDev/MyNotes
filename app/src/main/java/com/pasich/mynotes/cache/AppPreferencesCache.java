package com.pasich.mynotes.cache;

import android.util.Log;

import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import com.preference.PowerPreference;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Optimized cache for general app preferences (not theme-related).
 */
@Singleton
public class AppPreferencesCache {

    private static final String TAG = "AppPreferencesCache";

    // Cached values
    private volatile String lastKnownVersion;
    private volatile String sortPref;
    private volatile String tagsSortPref;
    private volatile int formatPref;

    private volatile boolean initialized = false;

    @Inject
    public AppPreferencesCache() {
    }

    /**
     * Initialize cache by loading values from SharedPreferences.
     */
    public synchronized void initialize() {
        if (initialized) return;

        try {
            lastKnownVersion = PowerPreference.getDefaultFile().getString(
                    PreferencesConfig.ARGUMENT_PREFERENCE_LAST_KNOWN_VERSION, "0"
            );

            sortPref = PowerPreference.getDefaultFile().getString(
                    PreferencesConfig.ARGUMENT_PREFERENCE_SORT,
                    PreferencesConfig.ARGUMENT_DEFAULT_SORT_PREF
            );

            tagsSortPref = PowerPreference.getDefaultFile().getString(
                    PreferencesConfig.ARGUMENT_PREFERENCE_TAGS_SORT,
                    PreferencesConfig.ARGUMENT_DEFAULT_TAGS_SORT_PREF
            );

            formatPref = PowerPreference.getDefaultFile().getInt(
                    PreferencesConfig.ARGUMENT_PREFERENCE_FORMAT,
                    PreferencesConfig.ARGUMENT_DEFAULT_FORMAT_VALUE
            );

            initialized = true;
            Log.d(TAG, "Cache initialized: version=" + lastKnownVersion
                    + ", sort=" + sortPref
                    + ", tagsSort=" + tagsSortPref
                    + ", format=" + formatPref);

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize cache", e);
            setDefaults();
        }
    }

    private void setDefaults() {
        lastKnownVersion = "0";
        sortPref = PreferencesConfig.ARGUMENT_DEFAULT_SORT_PREF;
        tagsSortPref = PreferencesConfig.ARGUMENT_DEFAULT_TAGS_SORT_PREF;
        formatPref = PreferencesConfig.ARGUMENT_DEFAULT_FORMAT_VALUE;
        initialized = true;
    }

    // ===================== GETTERS =====================

    public String getLastKnownVersion() {
        ensureInitialized();
        return lastKnownVersion;
    }

    public String getSortPref() {
        ensureInitialized();
        return sortPref;
    }

    public String getTagsSortPref() {
        ensureInitialized();
        return tagsSortPref;
    }

    public int getFormatPref() {
        ensureInitialized();
        return formatPref;
    }

    // ===================== SETTERS =====================

    public synchronized void setLastKnownVersion(String version) {
        try {
            lastKnownVersion = version;
            PowerPreference.getDefaultFile().putString(
                    PreferencesConfig.ARGUMENT_PREFERENCE_LAST_KNOWN_VERSION,
                    version
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to save version", e);
        }
    }

    public synchronized void setSortPref(String sort) {
        try {
            sortPref = sort;
            PowerPreference.getDefaultFile().putString(
                    PreferencesConfig.ARGUMENT_PREFERENCE_SORT,
                    sort
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to save sort preference", e);
        }
    }

    public synchronized void setTagsSortPref(String tagsSort) {
        try {
            tagsSortPref = tagsSort;
            PowerPreference.getDefaultFile().putString(
                    PreferencesConfig.ARGUMENT_PREFERENCE_TAGS_SORT,
                    tagsSort
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to save tags sort preference", e);
        }
    }

    public synchronized void setFormatPref(int format) {
        try {
            formatPref = format;
            PowerPreference.getDefaultFile().putInt(
                    PreferencesConfig.ARGUMENT_PREFERENCE_FORMAT,
                    format
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to save format preference", e);
        }
    }

    // ===================== HELPERS =====================

    private void ensureInitialized() {
        if (!initialized) {
            Log.w(TAG, "Cache not initialized, initializing now");
            initialize();
        }
    }


    public synchronized void refresh() {
        initialized = false;
        initialize();
    }
}
