package com.pasich.mynotes.ui.view.dialogs.theme;


import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.data.model.Theme;
import com.pasich.mynotes.utils.adapters.AccentColorAdapter;
import com.pasich.mynotes.utils.themes.ThemesArray;

import java.util.ArrayList;

public class AccentColorDialog {

    public static void show(Context ctx, ThemePreferencesCache cache, Callback callback) {
        if (ctx == null) return;

        ArrayList<Theme> themes = new ThemesArray().getThemes();
        if (themes.isEmpty()) return;

        int mode = ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        int[] colorResources = getAccentColors(mode);

        View dialogView = LayoutInflater.from(ctx)
                .inflate(R.layout.dialog_accent_picker, null);

        RecyclerView rv = dialogView.findViewById(R.id.accentGrid);
        rv.setLayoutManager(new GridLayoutManager(ctx, 4));
        rv.setAdapter(new AccentColorAdapter(
                ctx,
                themes,
                colorResources,
                cache.getThemeId(),
                selected -> {
                    cache.setThemeId(selected.getId());
                    callback.onSelected(selected);
                }
        ));

        new AlertDialog.Builder(ctx, R.style.Theme_MyNotes_Dialog)
                .setTitle(ctx.getString(R.string.selectAccentColor))
                .setView(dialogView)
                .setNegativeButton(R.string.close, null)
                .show();

    }

    private static int[] getAccentColors(int mode) {
        if (mode == Configuration.UI_MODE_NIGHT_YES) {
            return new int[]{
                    R.color.default_theme_dark_primary,
                    R.color.green_theme_dark_theme_primary,
                    R.color.red_pale_theme_dark_primary,
                    R.color.yellow_theme_dark_primary,
                    R.color.purple_theme_dark_primary,
                    R.color.silver_theme_dark_primary
            };
        } else {
            return new int[]{
                    R.color.default_theme_light_primary,
                    R.color.green_theme_light_theme_primary,
                    R.color.red_pale_theme_light_primary,
                    R.color.yellow_theme_light_primary,
                    R.color.purple_theme_light_primary,
                    R.color.silver_theme_primary
            };
        }
    }

    public interface Callback {
        void onSelected(@NonNull Theme selectedTheme);
    }
}
