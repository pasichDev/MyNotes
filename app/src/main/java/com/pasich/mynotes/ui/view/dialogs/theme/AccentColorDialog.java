package com.pasich.mynotes.ui.view.dialogs.theme;


import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.data.model.Theme;
import com.pasich.mynotes.utils.adapters.ThemeSelectionAdapter;
import com.pasich.mynotes.utils.themes.ThemesArray;

import java.util.ArrayList;

public class AccentColorDialog {

    public static void show(Context ctx, ThemePreferencesCache cache, Callback callback) {
        if (ctx == null) return;

        ArrayList<Theme> themes = new ThemesArray().getThemes();

        if (themes.isEmpty()) return;

        int currentNightMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        int[] colorResources = getAccentColors(currentNightMode);

        String[] names = new String[]{
                ctx.getString(R.string.themeBlue),
                ctx.getString(R.string.themeGreen),
                ctx.getString(R.string.themeSunset),
                ctx.getString(R.string.themeYellow),
                ctx.getString(R.string.themePurple),
                ctx.getString(R.string.themeCoralRed)
        };

        int maxItems = Math.min(themes.size(), names.length);

        ThemeSelectionAdapter adapter = new ThemeSelectionAdapter(
                ctx,
                themes,
                names,
                colorResources,
                cache.getThemeId()
        );

        AlertDialog dialog = new AlertDialog.Builder(ctx, R.style.Theme_MyNotes_Dialog)
                .setTitle(ctx.getString(R.string.selectAccentColor))
                .setAdapter(adapter, (d, which) -> {
                    if (which < maxItems) {
                        Theme selected = themes.get(which);
                        cache.setThemeId(selected.getId());
                        callback.onSelected(selected);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_rounded_background);
        }
    }

    private static int[] getAccentColors(int mode) {
        if (mode == Configuration.UI_MODE_NIGHT_YES) {
            return new int[]{
                    R.color.default_theme_dark_primary,
                    R.color.green_theme_dark_theme_primary,
                    R.color.red_pale_theme_dark_primary,
                    R.color.yellow_theme_dark_primary,
                    R.color.purple_theme_dark_primary,
                    R.color.red_pale_theme_dark_primary
            };
        } else {
            return new int[]{
                    R.color.default_theme_light_primary,
                    R.color.green_theme_light_theme_primary,
                    R.color.red_pale_theme_light_primary,
                    R.color.yellow_theme_light_primary,
                    R.color.purple_theme_light_primary,
                    R.color.red_pale_theme_light_primary
            };
        }
    }

    public interface Callback {
        void onSelected(@NonNull Theme selectedTheme);
    }
}
