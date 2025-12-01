package com.pasich.mynotes.utils.backup.models;

import com.google.gson.annotations.SerializedName;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;


/**
 * Backup model for user preferences.
 *
 * <p>This class stores all app + theme preferences required
 * to fully restore user configuration after importing a backup.</p>
 *
 * <p>Backward-compatible: new fields are optional and have
 * safe default values for old backups.</p>
 */
public class PreferencesBackup {

    // ================= OLD FIELDS =================

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

    @SerializedName("k")
    private final boolean imageOptimizationEnabled;


    @SerializedName("m")
    private final boolean screenProtection;

    @SerializedName("n")
    private final boolean extendedEditor;

    @SerializedName("o")
    private final float uiFontScale;


    public PreferencesBackup(
            int formatCount,
            String typeFace,
            String sortParam,
            int sizeText,
            int themeValue,
            boolean dynamicColor,
            int themeMode,
            boolean imageOptEnabled,
            boolean screenProtection,
            boolean extendedEditor,
            float uiFontScale
    ) {
        this.formatCount = formatCount;
        this.typeFaceNoteActivity = typeFace;
        this.sortParam = sortParam;
        this.sizeTextNote = sizeText;
        this.themeValue = themeValue;
        this.dynamicTheme = dynamicColor;
        this.themeMode = themeMode;
        this.imageOptimizationEnabled = imageOptEnabled;
        this.screenProtection = screenProtection;
        this.extendedEditor = extendedEditor;
        this.uiFontScale = uiFontScale;

        this.isCreated = true;
    }


    // ================= DEFAULT CONSTRUCTOR (IMPORT OLD BACKUP) =================

    public PreferencesBackup() {
        this.formatCount = PreferencesConfig.ARGUMENT_DEFAULT_FORMAT_VALUE;
        this.typeFaceNoteActivity = PreferencesConfig.ARGUMENT_DEFAULT_TEXT_STYLE;
        this.sortParam = PreferencesConfig.ARGUMENT_DEFAULT_SORT_PREF;
        this.sizeTextNote = PreferencesConfig.ARGUMENT_DEFAULT_TEXT_SIZE;
        this.themeValue = PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE;
        this.dynamicTheme = PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE;
        this.themeMode = PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE;

        this.imageOptimizationEnabled = PreferencesConfig.ARGUMENT_DEFAULT_IMAGEOPT_VALUE;
        this.screenProtection = PreferencesConfig.ARGUMENT_DEFAULT_SCREEN_PROTECTION_VALUE;
        this.extendedEditor = PreferencesConfig.ARGUMENT_DEFAULT_EXTENDED_EDITOR_VALUE;
        this.uiFontScale = PreferencesConfig.ARGUMENT_DEFAULT_UI_SCALING_VALUE;

        this.isCreated = false;
    }


    // ================= GETTERS =================

    public int getFormatCount() {
        return formatCount;
    }

    public int getSizeTextNote() {
        return sizeTextNote;
    }

    public int getThemeValue() {
        return themeValue;
    }

    public boolean isDynamicTheme() {
        return dynamicTheme;
    }

    public String getTypeFaceNoteActivity() {
        return typeFaceNoteActivity;
    }

    public String getSortParam() {
        return sortParam;
    }

    public boolean isCreated() {
        return isCreated;
    }

    public int getThemeMode() {
        return themeMode;
    }


    public boolean isImageOptimizationEnabled() {
        return imageOptimizationEnabled;
    }

    public boolean isScreenProtection() {
        return screenProtection;
    }

    public boolean isExtendedEditor() {
        return extendedEditor;
    }

    public float getUiFontScale() {
        return uiFontScale;
    }
}
