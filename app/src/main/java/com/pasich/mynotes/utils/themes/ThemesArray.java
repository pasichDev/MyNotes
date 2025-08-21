package com.pasich.mynotes.utils.themes;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Theme;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;

import java.util.ArrayList;

public class ThemesArray {

    private final ArrayList<Theme> themes = new ArrayList<>();

    private void initialThemes(boolean darkTheme) {
        themes.add(new Theme(0, R.style.DefaultTheme));
        themes.add(new Theme(1, R.style.GreenTheme));
        themes.add(new Theme( 2, R.style.PalePinkTheme));
        themes.add(new Theme( 3, R.style.YellowTheme));
        themes.add(new Theme( 4, R.style.PinkTheme));
    }

    public ArrayList<Theme> getThemes(boolean darkTheme) {
        initialThemes(darkTheme);
        return themes;
    }

    public int getThemeStyle(int themeID) {
        for (Theme theme : getThemes(false)) {
            if (theme.getId() == themeID) return theme.getTHEME_STYLE();
        }
        return PreferencesConfig.Theme_DEFAULT.getTHEME_STYLE();
    }
}
