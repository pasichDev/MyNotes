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

    /**
     * Writes every backed-up preference as one durable edit.
     *
     * @return true only when the whole set is durably stored.
     */
    boolean commitListPreferences(PreferencesBackup preferences);

    String getLastKnownVersion();

    void setLastKnownVersion(String version);

    boolean isSyncEnabled();

    void setSyncEnabled(boolean enabled);

    boolean isBackgroundSyncEnabled();

    void setBackgroundSyncEnabled(boolean enabled);

    boolean isFirstSyncConfirmed();

    void setFirstSyncConfirmed(boolean confirmed);
}
