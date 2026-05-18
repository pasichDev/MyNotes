package com.pasich.mynotes.data.preferences;

import com.pasich.mynotes.utils.backup.models.PreferencesBackup;

public interface PreferenceHelper {

    int getFormatCount();

    String getTypeFaceNoteActivity();

    int getSizeTextNoteActivity();

    String getSortParam();

    String getSortParamTags();

    void setSortParamTags(String paramTags);

    void editSizeTextNoteActivity(int value);

    PreferencesBackup getListPreferences();

    void setListPreferences(PreferencesBackup preferences);

    String getLastKnownVersion();

    void setLastKnownVersion(String version);
}
