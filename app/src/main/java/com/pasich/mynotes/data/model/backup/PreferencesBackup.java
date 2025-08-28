package com.pasich.mynotes.data.model.backup;

import com.google.gson.annotations.SerializedName;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;

public class PreferencesBackup {

    @SerializedName("a")
    private final int formatCount;
    
    @SerializedName("b")
    private final int sizeTextNote;
    
    @SerializedName("c")
    private final int themeValue;
    
    @SerializedName("d")
    private final boolean dynamicTheme;
    
    @SerializedName("e")
    private final String typeFaceNoteActivity;
    
    @SerializedName("f")
    private final String sortParam;
    
    @SerializedName("g")
    private final boolean isCreated;
    
    @SerializedName("h")
    private final int themeMode;

    public PreferencesBackup(int fc, String tf, String sp, int st, int tv, boolean dt, int tm) {
        this.formatCount = fc;
        this.typeFaceNoteActivity = tf;
        this.sortParam = sp;
        this.sizeTextNote = st;
        this.themeValue = tv;
        this.dynamicTheme = dt;
        this.themeMode = tm;
        this.isCreated = true;
    }

    public PreferencesBackup() {
        this.formatCount = PreferencesConfig.ARGUMENT_DEFAULT_FORMAT_VALUE;
        this.typeFaceNoteActivity = PreferencesConfig.ARGUMENT_DEFAULT_TEXT_STYLE;
        this.sortParam = PreferencesConfig.ARGUMENT_DEFAULT_SORT_PREF;
        this.sizeTextNote = PreferencesConfig.ARGUMENT_DEFAULT_TEXT_SIZE;
        this.themeValue = PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE;
        this.dynamicTheme = PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE;
        this.themeMode = PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE;
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

    public int getThemeMode() {
        return themeMode;
    }
}
