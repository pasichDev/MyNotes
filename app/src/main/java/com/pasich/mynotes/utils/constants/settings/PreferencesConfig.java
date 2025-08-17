package com.pasich.mynotes.utils.constants.settings;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Theme;

public class PreferencesConfig {
    //TextStyle
    public static final String ARGUMENT_PREFERENCE_TEXT_STYLE = "textStyle";
    public static final String ARGUMENT_DEFAULT_TEXT_STYLE = "normal";
    //TextSize
    public static final String ARGUMENT_PREFERENCE_TEXT_SIZE = "textSize";
    public static final int ARGUMENT_DEFAULT_TEXT_SIZE = 16;
    //SortPrefix
    public static final String ARGUMENT_PREFERENCE_SORT = "sortPref";
    public static final String ARGUMENT_DEFAULT_SORT_PREF = "DataSort";
    //FormatPrefix
    public static final String ARGUMENT_PREFERENCE_FORMAT = "formatParam";
    public static final int ARGUMENT_DEFAULT_FORMAT_VALUE = 1;
    //THEME
    public static final String ARGUMENT_PREFERENCE_THEME = "appTheme";
    public static final int ARGUMENT_DEFAULT_THEME_VALUE = 0;
    public static final Theme Theme_DEFAULT = new Theme(R.drawable.ic_theme_darkblue, 0, R.style.DefaultTheme, R.drawable.item_theme_check_blue);
    //DynamicColors
    public static final String ARGUMENT_PREFERENCE_DYNAMIC_COLOR = "dynamicColorEnable";
    public static final boolean ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE = false;
}
