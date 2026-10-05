package com.pasich.mynotes.cache;

import android.util.Log;
import com.pasich.mynotes.data.preferences.SafePreferences;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import com.pasich.mynotes.utils.constants.settings.SortParam;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Optimized cache for general app preferences (not theme-related). */
@Singleton
public class AppPreferencesCache {

    private static final String TAG = "AppPreferencesCache";
    private final SafePreferences prefs;
    private volatile String lastKnownVersion;
    private volatile String sortPref;
    private volatile boolean customOrder;
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

            customOrder =
                    prefs.getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_CUSTOM_ORDER, false);

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
        customOrder = false;
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

    /** Whether the one-time "Meet Encly" introduction has been shown. */
    public boolean isMeetEnclyShown() {
        return prefs.getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_MEET_ENCLY_SHOWN, false);
    }

    public synchronized void setMeetEnclyShown() {
        try {
            prefs.putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_MEET_ENCLY_SHOWN, true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save meet-encly flag", e);
        }
    }

    /** The notes order in effect: {@link SortParam#Custom} or the synced date order. */
    public String getSortPref() {
        ensureInitialized();
        return customOrder ? SortParam.Custom : getSyncedSortPref();
    }

    /**
     * The date order that is backed up and synced, never {@link SortParam#Custom}: older versions
     * of the app read anything but "newest first" as "oldest first", and the custom order itself
     * only exists on this device.
     */
    public String getSyncedSortPref() {
        ensureInitialized();
        return SortParam.Custom.equals(sortPref) ? SortParam.DataSort : sortPref;
    }

    /**
     * Whether the hint about dragging in the custom order may be shown after a long press: never in
     * the custom order itself, in selection or in search, and only once ever.
     */
    public static boolean customOrderHintDue(
            boolean customOrder, boolean selecting, boolean searching, boolean shownBefore) {
        return !customOrder && !selecting && !searching && !shownBefore;
    }

    public boolean isCustomOrderHintShown() {
        return prefs.getBoolean(
                PreferencesConfig.ARGUMENT_PREFERENCE_CUSTOM_ORDER_HINT_SHOWN, false);
    }

    public void setCustomOrderHintShown() {
        prefs.putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_CUSTOM_ORDER_HINT_SHOWN, true);
    }

    public synchronized void setSortPref(String sort) {
        try {
            boolean custom = SortParam.Custom.equals(sort);
            customOrder = custom;
            prefs.putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_CUSTOM_ORDER, custom);
            if (!custom) {
                sortPref = sort;
                prefs.putString(PreferencesConfig.ARGUMENT_PREFERENCE_SORT, sort);
            }
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
