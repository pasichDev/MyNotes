package com.pasich.mynotes.ui.view.dialogs.theme;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.ThemePreferencesCache;

public class ThemeModeDialog {

    public interface Callback {
        void onSelected(int mode);
    }

    public static void show(Context ctx, ThemePreferencesCache cache, Callback callback) {
        String[] names = {
                ctx.getString(R.string.themeModeFollowSystem),
                ctx.getString(R.string.themeModeLight),
                ctx.getString(R.string.themeModeDark)
        };

        int[] icons = {
                R.drawable.ic_auto_mode,
                R.drawable.ic_light_mode,
                R.drawable.ic_dark_mode
        };

        int current = cache.getThemeMode();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx,
                R.layout.item_theme_mode_dialog, R.id.themeModeName, names) {

            @NonNull
            @Override
            public View getView(int position, View convertView,
                                @NonNull ViewGroup parent) {

                View view = super.getView(position, convertView, parent);
                ImageView icon = view.findViewById(R.id.themeModeIcon);
                ImageView selected = view.findViewById(R.id.selectedModeIndicator);
                TextView title = view.findViewById(R.id.themeModeName);

                icon.setImageResource(icons[position]);

                boolean isSelected = position == current;
                selected.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                title.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);

                return view;
            }
        };

        new AlertDialog.Builder(ctx, R.style.Theme_MyNotes_Dialog)
                .setTitle(ctx.getString(R.string.selectThemeMode))
                .setAdapter(adapter, (d, which) -> {
                    cache.setThemeMode(which);
                    callback.onSelected(which);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
