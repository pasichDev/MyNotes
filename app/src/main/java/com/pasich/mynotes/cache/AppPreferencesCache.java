package com.pasich.mynotes.cache;

import android.util.Log;
import com.pasich.mynotes.data.preferences.SafePreferences;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Optimized cache for general app preferences (not theme-related). */
@Singleton
public class AppPreferencesCache {

    private static final String TAG = "AppPreferencesCache";
    private final SafePreferences prefs;
    private volatile String lastKnownVersion;
    private volatile String sortPref;
    private volatile String tagsSortPref;
    private volatile int formatPref;

    private volatile boolean imageOptEnable;
    private volatile boolean initialized = false;

    @Inject
    public AppPreferencesCache(SafePreferences prefs) {
        this.prefs = prefs;
    }

    /** Initialize cache by loading values from SharedPreferences. */
    public synchronized void initialize() {
        if (initialized) return;

        try {
            lastKnownVersion =
                    prefs.getString(PreferencesConfig.ARGUMENT_PREFERENCE_LAST_KNOWN_VERSION, "0");

            sortPref =
                    prefs.getString(
                            PreferencesConfig.ARGUMENT_PREFERENCE_SORT,
                            PreferencesConfig.ARGUMENT_DEFAULT_SORT_PREF);

            tagsSortPref =
                    prefs.getString(
                            PreferencesConfig.ARGUMENT_PREFERENCE_TAGS_SORT,
                            PreferencesConfig.ARGUMENT_DEFAULT_TAGS_SORT_PREF);

            formatPref =
                    prefs.getInt(
                            PreferencesConfig.ARGUMENT_PREFERENCE_FORMAT,
                            PreferencesConfig.ARGUMENT_DEFAULT_FORMAT_VALUE);

            imageOptEnable =
                    prefs.getBoolean(
                            PreferencesConfig.ARGUMENT_PREFERENCE_IMAGEOPT,
                            PreferencesConfig.ARGUMENT_DEFAULT_IMAGEOPT_VALUE);

            initialized = true;

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
        imageOptEnable = PreferencesConfig.ARGUMENT_DEFAULT_IMAGEOPT_VALUE;
        initialized = true;
    }

    public String getLastKnownVersion() {
        ensureInitialized();
        return lastKnownVersion;
    }

    public synchronized void setLastKnownVersion(String version) {
        try {
            lastKnownVersion = version;
            prefs.putString(PreferencesConfig.ARGUMENT_PREFERENCE_LAST_KNOWN_VERSION, version);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save version", e);
        }
    }

    public String getSortPref() {
        ensureInitialized();
        return sortPref;
    }

    public synchronized void setSortPref(String sort) {
        try {
            sortPref = sort;
            prefs.putString(PreferencesConfig.ARGUMENT_PREFERENCE_SORT, sort);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save sort preference", e);
        }
    }

    public boolean getImageOpt() {
        ensureInitialized();
        return imageOptEnable;
    }

    public synchronized void setImageOpt(boolean mImageOpt) {
        try {
            imageOptEnable = mImageOpt;
            prefs.putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_IMAGEOPT, mImageOpt);
        } catch (Exception e) {
            Log.e(TAG, "Failed to image_opt preference", e);
        }
    }

    public String getTagsSortPref() {
        ensureInitialized();
        return tagsSortPref;
    }

    public synchronized void setTagsSortPref(String tagsSort) {
        try {
            tagsSortPref = tagsSort;
            prefs.putString(PreferencesConfig.ARGUMENT_PREFERENCE_TAGS_SORT, tagsSort);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save tags sort preference", e);
        }
    }

    public int getFormatPref() {
        ensureInitialized();
        return formatPref;
    }

    public synchronized void setFormatPref(int format) {
        try {
            formatPref = format;
            prefs.putInt(PreferencesConfig.ARGUMENT_PREFERENCE_FORMAT, format);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save format preference", e);
        }
    }

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
