package com.pasich.mynotes.data.preferences;


import com.pasich.mynotes.data.model.backup.PreferencesBackup;
import com.preference.Preference;


public interface PreferenceHelper {

    Preference getDefaultPreferences();

    Preference getBackupCloudInfoPreference();

    int getFormatCount();

    String getTypeFaceNoteActivity();

    int getSizeTextNoteActivity();

    String getSortParam();

    String getSortParamTags();

    void setSortParamTags(String paramTags);

    long getLastDataBackupCloud();

    String getLastBackupCloudId();

    void editSizeTextNoteActivity(int value);

    int getSetCloudAuthBackup();

    PreferencesBackup getListPreferences();

    void setListPreferences(PreferencesBackup preferences);

    String getLastKnownVersion();

    void setLastKnownVersion(String version);

    @Deprecated
    void cleanBackupInfo();
}
