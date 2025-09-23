package com.pasich.mynotes.utils.themes;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Theme;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;

import java.util.ArrayList;

public class ThemesArray {

    private final ArrayList<Theme> themes = new ArrayList<>();

    private void initialThemes() {
        themes.add(new Theme(0, R.style.DefaultTheme));
        themes.add(new Theme(1, R.style.GreenTheme));
        themes.add(new Theme(2, R.style.SunsetTheme));
        themes.add(new Theme(3, R.style.YellowTheme));
        themes.add(new Theme(4, R.style.PurpleTheme));
    }

    public ArrayList<Theme> getThemes() {
        initialThemes();
        return themes;
    }

    public int getThemeStyle(int themeID) {
        for (Theme theme : getThemes()) {
            if (theme.getId() == themeID) return theme.getTHEME_STYLE();
        }
        return PreferencesConfig.Theme_DEFAULT.getTHEME_STYLE();
    }
}
