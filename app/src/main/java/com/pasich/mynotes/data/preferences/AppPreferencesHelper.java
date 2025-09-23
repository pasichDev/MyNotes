package com.pasich.mynotes.data.preferences;

import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.ARGUMENT_AUTO_BACKUP_CLOUD;
import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.ARGUMENT_AUTO_BACKUP_CLOUD_ID;
import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.ARGUMENT_DEFAULT_LAST_BACKUP_ID;
import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.ARGUMENT_DEFAULT_LAST_BACKUP_TIME;
import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.ARGUMENT_LAST_BACKUP_ID;
import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.ARGUMENT_LAST_BACKUP_TIME;
import static com.pasich.mynotes.utils.constants.settings.BackupPreferences.FIlE_NAME_PREFERENCE_BACKUP;
import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_PREFERENCE_SORT;
import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_SIZE;
import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_STYLE;

import com.pasich.mynotes.cache.AppPreferencesCache;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.data.model.backup.PreferencesBackup;
import com.pasich.mynotes.utils.constants.settings.BackupPreferences;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import com.preference.PowerPreference;
import com.preference.Preference;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AppPreferencesHelper implements PreferenceHelper {

    private final AppPreferencesCache appCache;

    private final ThemePreferencesCache themeCache;

    @Inject
    AppPreferencesHelper(AppPreferencesCache appCache, ThemePreferencesCache themeCache) {
        this.appCache = appCache;
        this.themeCache = themeCache;
        this.appCache.initialize();
        this.themeCache.initialize();
    }

    @Override
    public Preference getDefaultPreferences() {
        return PowerPreference.getDefaultFile();
    }

    @Deprecated
    @Override
    public Preference getBackupCloudInfoPreference() {
        return PowerPreference.getFileByName(FIlE_NAME_PREFERENCE_BACKUP);
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

    ///  TODO Migrate to AppPreferencesCache
    @Deprecated
    @Override
    public long getLastDataBackupCloud() {
        return getBackupCloudInfoPreference().getLong(ARGUMENT_LAST_BACKUP_TIME, ARGUMENT_DEFAULT_LAST_BACKUP_TIME);
    }

    @Override
    public String getLastBackupCloudId() {
        return getBackupCloudInfoPreference().getString(ARGUMENT_LAST_BACKUP_ID, ARGUMENT_DEFAULT_LAST_BACKUP_ID);
    }

    @Override
    public void editSizeTextNoteActivity(int value) {
        themeCache.setSizeTextNoteActivity(value);
    }

    @Deprecated
    @Override
    public int getSetCloudAuthBackup() {
        return getBackupCloudInfoPreference().getInt(ARGUMENT_AUTO_BACKUP_CLOUD, ARGUMENT_AUTO_BACKUP_CLOUD_ID);
    }

    @Override
    public PreferencesBackup getListPreferences() {
        return new PreferencesBackup(
                getFormatCount(),
                getTypeFaceNoteActivity(),
                getSortParam(),
                getSizeTextNoteActivity(),
                PowerPreference.getDefaultFile().getInt(
                        PreferencesConfig.ARGUMENT_PREFERENCE_THEME,
                        PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE
                ),
                PowerPreference.getDefaultFile().getBoolean(
                        PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR,
                        PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE
                ),
                PowerPreference.getDefaultFile().getInt(
                        PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE,
                        PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE
                )
        );
    }

    @Override
    public void setListPreferences(PreferencesBackup preferences) {
        if (preferences.isCreated()) {
            getDefaultPreferences()
                    .putInt(PreferencesConfig.ARGUMENT_PREFERENCE_FORMAT, preferences.getFormatCount())
                    .putString(ARGUMENT_PREFERENCE_TEXT_STYLE, preferences.getTypeFaceNoteActivity())
                    .putString(ARGUMENT_PREFERENCE_SORT, preferences.getSortParam())
                    .putInt(ARGUMENT_PREFERENCE_TEXT_SIZE, preferences.getSizeTextNote())
                    .putInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, preferences.getThemeValue())
                    .putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, preferences.isDynamicTheme())
                    .putInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, preferences.getThemeMode());

            appCache.refresh();
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

    @Override
    public void cleanBackupInfo() {
        PowerPreference.getFileByName(BackupPreferences.FIlE_NAME_PREFERENCE_BACKUP).clearAsync();
    }

}
