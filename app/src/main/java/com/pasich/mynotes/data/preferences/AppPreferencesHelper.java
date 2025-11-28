package com.pasich.mynotes.data.preferences;

import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_PREFERENCE_SORT;
import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_SIZE;
import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_STYLE;

import com.pasich.mynotes.cache.AppPreferencesCache;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.data.model.backup.PreferencesBackup;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AppPreferencesHelper implements PreferenceHelper {

    private final AppPreferencesCache appCache;

    private final ThemePreferencesCache themeCache;
    private final SafePreferences prefs;

    @Inject
    AppPreferencesHelper(AppPreferencesCache appCache, ThemePreferencesCache themeCache, SafePreferences prefs) {
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
                getFormatCount(),
                getTypeFaceNoteActivity(),
                getSortParam(),
                getSizeTextNoteActivity(),

                prefs.getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME,
                        PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE),

                prefs.getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR,
                        PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE),

                prefs.getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE,
                        PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE)
        );
    }

    @Override
    public void setListPreferences(PreferencesBackup preferences) {
        if (preferences.isCreated()) {

            prefs.putInt(PreferencesConfig.ARGUMENT_PREFERENCE_FORMAT,
                    preferences.getFormatCount());

            prefs.putString(ARGUMENT_PREFERENCE_TEXT_STYLE,
                    preferences.getTypeFaceNoteActivity());

            prefs.putString(ARGUMENT_PREFERENCE_SORT,
                    preferences.getSortParam());

            prefs.putInt(ARGUMENT_PREFERENCE_TEXT_SIZE,
                    preferences.getSizeTextNote());

            prefs.putInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME,
                    preferences.getThemeValue());

            prefs.putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR,
                    preferences.isDynamicTheme());

            prefs.putInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE,
                    preferences.getThemeMode());

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
