package com.pasich.mynotes.data.model.backup;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;

public class PreferencesBackup {

    private final int formatCount;
    private final int sizeTextNote;
    private final int themeValue;
    private final boolean dynamicTheme;
    private final String typeFaceNoteActivity;
    private final String sortParam;
    private final boolean isCreated;

    public PreferencesBackup(int fc, String tf, String sp, int st, int tv, boolean dt) {
        this.formatCount = fc;
        this.typeFaceNoteActivity = tf;
        this.sortParam = sp;
        this.sizeTextNote = st;
        this.themeValue = tv;
        this.dynamicTheme = dt;
        this.isCreated = true;
    }

    public PreferencesBackup() {
        this.formatCount = PreferencesConfig.ARGUMENT_DEFAULT_FORMAT_VALUE;
        this.typeFaceNoteActivity = PreferencesConfig.ARGUMENT_DEFAULT_TEXT_STYLE;
        this.sortParam = PreferencesConfig.ARGUMENT_DEFAULT_SORT_PREF;
        this.sizeTextNote = PreferencesConfig.ARGUMENT_DEFAULT_TEXT_SIZE;
        this.themeValue = PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE;
        this.dynamicTheme = PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE;
        this.isCreated = false;
    }

    public int getFormatCount() {
        return formatCount;
    }

    public int getSizeTextNote() {
        return sizeTextNote;
    }

    public int getThemeValue() {
        return themeValue;
    }

    public String getSortParam() {
        return sortParam;
    }

    public String getTypeFaceNoteActivity() {
        return typeFaceNoteActivity;
    }

    public boolean isDynamicTheme() {
        return dynamicTheme;
    }

    public boolean isCreated() {
        return isCreated;
    }
}
