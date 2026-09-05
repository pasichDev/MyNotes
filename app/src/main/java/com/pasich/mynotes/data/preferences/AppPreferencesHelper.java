package com.pasich.mynotes.data.preferences;

import com.pasich.mynotes.cache.AppPreferencesCache;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.utils.backup.models.PreferencesBackup;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Singleton implementation of PreferenceHelper backed by caches. */
@Singleton
public class AppPreferencesHelper implements PreferenceHelper {

    private final AppPreferencesCache appCache;

    private final ThemePreferencesCache themeCache;
    private final SafePreferences prefs;
    private final java.util.concurrent.Executor mainThread;

    @Inject
    AppPreferencesHelper(
            AppPreferencesCache appCache, ThemePreferencesCache themeCache, SafePreferences prefs) {
        this(
                appCache,
                themeCache,
                prefs,
                new android.os.Handler(android.os.Looper.getMainLooper())::post);
    }

    /** Test seam: where the theme application is posted to. */
    AppPreferencesHelper(
            AppPreferencesCache appCache,
            ThemePreferencesCache themeCache,
            SafePreferences prefs,
            java.util.concurrent.Executor mainThread) {
        this.prefs = prefs;
        this.appCache = appCache;
        this.themeCache = themeCache;
        this.mainThread = mainThread;
        this.appCache.initialize();
        this.themeCache.initialize();
    }

    @Override
    public int getFormatCount() {
        return appCache.getFormatPref();
    }

    @Override
    public int getSizeTextNoteActivity() {
        return themeCache.getSizeTextNoteActivity();
    }

    @Override
    public String getSortParam() {
        return appCache.getSortPref();
    }

    @Override
    public String getSortParamTags() {
        return appCache.getTagsSortPref();
    }

    @Override
    public void setSortParamTags(String paramTags) {
        appCache.setTagsSortPref(paramTags);
    }

    @Override
    public void editSizeTextNoteActivity(int value) {
        themeCache.setSizeTextNoteActivity(value);
    }

    /** Returns a snapshot of all current app preferences. */
    @Override
    public PreferencesBackup getListPreferences() {
        return new PreferencesBackup(
                // OLD FIELDS
                getFormatCount(),
                getTypeFaceNoteActivity(),
                getSortParam(),
                getSizeTextNoteActivity(),
                prefs.getInt(
                        PreferencesConfig.ARGUMENT_PREFERENCE_THEME,
                        PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE),
                prefs.getBoolean(
                        PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR,
                        PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE),
                prefs.getInt(
                        PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE,
                        PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE),
                prefs.getBoolean(
                        PreferencesConfig.ARGUMENT_PREFERENCE_IMAGEOPT,
                        PreferencesConfig.ARGUMENT_DEFAULT_IMAGEOPT_VALUE),
                prefs.getBoolean(
                        PreferencesConfig.ARGUMENT_PREFERENCE_SCREEN_PROTECTION,
                        PreferencesConfig.ARGUMENT_DEFAULT_SCREEN_PROTECTION_VALUE),
                prefs.getBoolean(
                        PreferencesConfig.ARGUMENT_PREFERENCE_EXTENDED_EDITOR,
                        PreferencesConfig.ARGUMENT_DEFAULT_EXTENDED_EDITOR_VALUE),
                prefs.getFloat(
                        PreferencesConfig.ARGUMENT_PREFERENCE_UI_SCALING,
                        PreferencesConfig.ARGUMENT_DEFAULT_UI_SCALING_VALUE));
    }

    /**
     * Persists all fields from a backup and refreshes the caches.
     *
     * <p>The restore path. The theme is deliberately not applied here: {@code
     * AppCompatDelegate.setDefaultNightMode} recreates every started activity when the mode
     * changes, and this runs at the start of a restore whose note and tag inserts are still in
     * flight on the Backup screen — recreating it disposed those inserts and left the database half
     * restored with no message. The stored mode takes effect at the next activity creation, and the
     * screen applies it itself once the restore has finished.
     */
    @Override
    public void setListPreferences(PreferencesBackup preferences) {
        commitListPreferences(preferences, false);
    }

    /**
     * Writes every backed-up preference as one durable edit.
     *
     * <p>This used to be eleven separate {@code apply()} calls. {@code apply()} is asynchronous and
     * per key, so a process death part-way through left the user with a mixture of the old and the
     * new settings, and left the sync journal that had "already committed" them cleared. One editor
     * plus {@code commit()} makes the whole set atomic and tells the caller whether it is durable,
     * which is what lets {@code RoomSyncStore} decide when the journal may be dropped.
     *
     * <p>The sync path: a theme arriving from another device is applied at once.
     *
     * @return true when the values are durably stored, false when the write failed.
     */
    @Override
    public boolean commitListPreferences(PreferencesBackup preferences) {
        return commitListPreferences(preferences, true);
    }

    private boolean commitListPreferences(PreferencesBackup preferences, boolean applyThemeNow) {
        if (preferences == null || !preferences.isCreated()) {
            return false;
        }
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put(PreferencesConfig.ARGUMENT_PREFERENCE_FORMAT, preferences.getFormatCount());
        values.put(
                PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_STYLE,
                preferences.getTypeFaceNoteActivity());
        values.put(PreferencesConfig.ARGUMENT_PREFERENCE_SORT, preferences.getSortParam());
        values.put(PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_SIZE, preferences.getSizeTextNote());
        values.put(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, preferences.getThemeValue());
        values.put(
                PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, preferences.isDynamicTheme());
        values.put(PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, preferences.getThemeMode());
        values.put(
                PreferencesConfig.ARGUMENT_PREFERENCE_IMAGEOPT,
                preferences.isImageOptimizationEnabled());
        values.put(
                PreferencesConfig.ARGUMENT_PREFERENCE_SCREEN_PROTECTION,
                preferences.isScreenProtection());
        values.put(
                PreferencesConfig.ARGUMENT_PREFERENCE_EXTENDED_EDITOR,
                preferences.isExtendedEditor());
        values.put(PreferencesConfig.ARGUMENT_PREFERENCE_UI_SCALING, preferences.getUiFontScale());

        if (!prefs.commitAll(values)) {
            return false;
        }
        appCache.refresh();
        themeCache.refresh();
        if (applyThemeNow) {
            // Refreshing the caches only reloads the values. Light/dark is owned by
            // AppCompatDelegate, which has to be told, or a theme arriving from another device
            // sat in storage until the next activity was created. Posted to the main thread
            // because a sync apply runs on a background thread.
            mainThread.execute(themeCache::applyCurrentThemeMode);
        }
        return true;
    }

    @Override
    public String getTypeFaceNoteActivity() {
        return themeCache.getTypeFaceNoteActivity();
    }

    @Override
    public String getLastKnownVersion() {
        return appCache.getLastKnownVersion();
    }

    @Override
    public void setLastKnownVersion(String version) {
        appCache.setLastKnownVersion(version);
    }

    @Override
    public boolean isSyncEnabled() {
        return prefs.getBoolean(
                PreferencesConfig.ARGUMENT_PREFERENCE_SYNC_ENABLED,
                PreferencesConfig.ARGUMENT_DEFAULT_SYNC_ENABLED);
    }

    @Override
    public void setSyncEnabled(boolean enabled) {
        prefs.putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_SYNC_ENABLED, enabled);
    }

    @Override
    public boolean isBackgroundSyncEnabled() {
        return prefs.getBoolean(
                PreferencesConfig.ARGUMENT_PREFERENCE_SYNC_BACKGROUND_ENABLED,
                PreferencesConfig.ARGUMENT_DEFAULT_SYNC_BACKGROUND_ENABLED);
    }

    @Override
    public void setBackgroundSyncEnabled(boolean enabled) {
        prefs.putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_SYNC_BACKGROUND_ENABLED, enabled);
    }

    @Override
    public boolean isFirstSyncConfirmed() {
        return prefs.getBoolean(
                PreferencesConfig.ARGUMENT_PREFERENCE_SYNC_FIRST_CONFIRMED,
                PreferencesConfig.ARGUMENT_DEFAULT_SYNC_FIRST_CONFIRMED);
    }

    @Override
    public void setFirstSyncConfirmed(boolean confirmed) {
        prefs.putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_SYNC_FIRST_CONFIRMED, confirmed);
    }
}
