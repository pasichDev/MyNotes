package com.pasich.mynotes.utils.constants.settings;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Theme;

public class PreferencesConfig {
    // TextStyle
    public static final String ARGUMENT_PREFERENCE_TEXT_STYLE = "textStyle";
    public static final String ARGUMENT_DEFAULT_TEXT_STYLE = "normal";
    // TextSize
    public static final String ARGUMENT_PREFERENCE_TEXT_SIZE = "textSize";
    public static final int ARGUMENT_DEFAULT_TEXT_SIZE = 16;
    public static final int NOTE_TEXT_SIZE_MIN = 10;
    public static final int NOTE_TEXT_SIZE_MAX = 32;

    /** Keeps the persisted note text size compatible with the editor Slider range. */
    public static int normalizeNoteTextSize(int size) {
        return Math.max(NOTE_TEXT_SIZE_MIN, Math.min(size, NOTE_TEXT_SIZE_MAX));
    }

    // SortPrefix
    public static final String ARGUMENT_PREFERENCE_SORT = "sortPref";
    public static final String ARGUMENT_DEFAULT_SORT_PREF = SortParam.DataSort;
    // TagsSortPrefix
    public static final String ARGUMENT_PREFERENCE_TAGS_SORT = "tagsSortPref";
    public static final String ARGUMENT_DEFAULT_TAGS_SORT_PREF = "TagsCreationDateSort";
    // FormatPrefix
    public static final String ARGUMENT_PREFERENCE_FORMAT = "formatParam";
    public static final int ARGUMENT_DEFAULT_FORMAT_VALUE = 1;
    // THEME
    public static final String ARGUMENT_PREFERENCE_THEME = "appTheme";
    public static final int ARGUMENT_DEFAULT_THEME_VALUE = 0;
    public static final Theme Theme_DEFAULT = new Theme(0, R.style.DefaultTheme);
    // DynamicColors
    public static final String ARGUMENT_PREFERENCE_DYNAMIC_COLOR = "dynamicColorEnable";
    public static final boolean ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE = false;
    // ScreenProtection
    public static final String ARGUMENT_PREFERENCE_SCREEN_PROTECTION = "screenProtectionEnable";
    public static final boolean ARGUMENT_DEFAULT_SCREEN_PROTECTION_VALUE = false;
    // ExtendedEditor
    public static final String ARGUMENT_PREFERENCE_EXTENDED_EDITOR = "extendedEditorEnable";
    public static final boolean ARGUMENT_DEFAULT_EXTENDED_EDITOR_VALUE = true; // default new user
    // LastKnownVersion
    public static final String ARGUMENT_PREFERENCE_LAST_KNOWN_VERSION = "lastKnownVersion";
    // ThemeMode
    public static final String ARGUMENT_PREFERENCE_THEME_MODE = "themeMode";
    public static final int ARGUMENT_DEFAULT_THEME_MODE_VALUE =
            0; // 0 = Follow System, 1 = Light, 2 = Dark
    // Image extended editor optimization
    public static final String ARGUMENT_PREFERENCE_IMAGEOPT = "image_opt";
    public static final boolean ARGUMENT_DEFAULT_IMAGEOPT_VALUE = false;

    // font scaling app
    public static final String ARGUMENT_PREFERENCE_UI_SCALING = "uiFontScale";
    public static final float ARGUMENT_DEFAULT_UI_SCALING_VALUE = 0.0f;
    // Notification sound / channel
    public static final String ARGUMENT_PREFERENCE_NOTIFICATION_SOUND_URI =
            "notification_sound_uri";
    public static final String ARGUMENT_PREFERENCE_NOTIFICATION_CHANNEL_ID =
            "notification_channel_id";
    public static final String ARGUMENT_PREFERENCE_NOTIFICATION_CHANNEL_VERSION =
            "notification_channel_version";
    public static final String ARGUMENT_DEFAULT_NOTIFICATION_CHANNEL_ID = "reminders_v1";
    public static final int ARGUMENT_DEFAULT_NOTIFICATION_CHANNEL_VERSION = 1;

    // Google Drive sync
    public static final String ARGUMENT_PREFERENCE_SYNC_ENABLED = "sync_enabled";
    public static final String ARGUMENT_PREFERENCE_SYNC_BACKGROUND_ENABLED =
            "sync_background_enabled";
    public static final String ARGUMENT_PREFERENCE_SYNC_FIRST_CONFIRMED = "sync_first_confirmed";
    public static final String ARGUMENT_PREFERENCE_SYNC_ROLLOUT_BUCKET = "sync_rollout_bucket";
    public static final boolean ARGUMENT_DEFAULT_SYNC_ENABLED = false;
    public static final boolean ARGUMENT_DEFAULT_SYNC_BACKGROUND_ENABLED = false;
    public static final boolean ARGUMENT_DEFAULT_SYNC_FIRST_CONFIRMED = false;
    public static final int ARGUMENT_DEFAULT_SYNC_ROLLOUT_BUCKET = -1;
}
