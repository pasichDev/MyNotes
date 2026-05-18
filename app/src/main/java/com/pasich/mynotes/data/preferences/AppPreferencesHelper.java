package com.pasich.mynotes.data.preferences;

import com.pasich.mynotes.cache.AppPreferencesCache;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.utils.backup.models.PreferencesBackup;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AppPreferencesHelper implements PreferenceHelper {

    private final AppPreferencesCache appCache;

    private final ThemePreferencesCache themeCache;
    private final SafePreferences prefs;

    @Inject
    AppPreferencesHelper(
            AppPreferencesCache appCache, ThemePreferencesCache themeCache, SafePreferences prefs) {
        this.prefs = prefs;
        this.appCache = appCache;
        this.themeCache = themeCache;
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

    @Override
    public void setListPreferences(PreferencesBackup preferences) {

        if (preferences.isCreated()) {

            // OLD FIELDS
            prefs.putInt(
                    PreferencesConfig.ARGUMENT_PREFERENCE_FORMAT, preferences.getFormatCount());

            prefs.putString(
                    PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_STYLE,
                    preferences.getTypeFaceNoteActivity());

            prefs.putString(PreferencesConfig.ARGUMENT_PREFERENCE_SORT, preferences.getSortParam());

            prefs.putInt(
                    PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_SIZE, preferences.getSizeTextNote());

            prefs.putInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, preferences.getThemeValue());

            prefs.putBoolean(
                    PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR,
                    preferences.isDynamicTheme());

            prefs.putInt(
                    PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, preferences.getThemeMode());

            prefs.putBoolean(
                    PreferencesConfig.ARGUMENT_PREFERENCE_IMAGEOPT,
                    preferences.isImageOptimizationEnabled());

            prefs.putBoolean(
                    PreferencesConfig.ARGUMENT_PREFERENCE_SCREEN_PROTECTION,
                    preferences.isScreenProtection());

            prefs.putBoolean(
                    PreferencesConfig.ARGUMENT_PREFERENCE_EXTENDED_EDITOR,
                    preferences.isExtendedEditor());

            prefs.putFloat(
                    PreferencesConfig.ARGUMENT_PREFERENCE_UI_SCALING, preferences.getUiFontScale());

            // Refresh caches
            appCache.refresh();
            themeCache.refresh();
        }
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
}
