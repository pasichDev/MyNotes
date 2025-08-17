package com.pasich.mynotes.utils.themes;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Theme;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;

import java.util.ArrayList;

public class ThemesArray {

    private final ArrayList<Theme> themes = new ArrayList<>();

    private void initialThemes(boolean darkTheme) {
        themes.add(new Theme(darkTheme ? R.drawable.theme_default_dark : R.drawable.theme_default, 0, R.style.DefaultTheme, R.color.default_theme_light_primary));
        themes.add(new Theme(darkTheme ? R.drawable.theme_green_dark : R.drawable.theme_green, 1, R.style.GreenTheme, R.color.green_theme_light_primary));
        themes.add(new Theme(darkTheme ? R.drawable.theme_pale_pink_dark : R.drawable.theme_pale_pink, 2, R.style.PalePinkTheme, R.color.pale_pink_theme_light_primary));
        themes.add(new Theme(darkTheme ? R.drawable.theme_yellow_dark : R.drawable.theme_yellow, 3, R.style.YellowTheme, R.color.yellow_theme_light_primary));
        themes.add(new Theme(darkTheme ? R.drawable.theme_pink_dark : R.drawable.theme_pink, 4, R.style.PinkTheme, R.color.pink_theme_light_primary));
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
