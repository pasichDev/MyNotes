package com.pasich.mynotes.utils.adapters;


import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Theme;

import java.util.ArrayList;

public class ThemeSelectionAdapter extends ArrayAdapter<String> {

    private final Context ctx;
    private final ArrayList<Theme> themes;
    private final String[] names;
    private final int[] colorRes;
    private final int currentThemeId;

    public ThemeSelectionAdapter(
            @NonNull Context context,
            @NonNull ArrayList<Theme> themes,
            @NonNull String[] names,
            @NonNull int[] colorRes,
            int currentThemeId
    ) {
        super(context, R.layout.item_theme_dialog, R.id.themeName, names);
        this.ctx = context;
        this.themes = themes;
        this.names = names;
        this.colorRes = colorRes;
        this.currentThemeId = currentThemeId;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView,
                        @NonNull ViewGroup parent) {

        View view = super.getView(position, convertView, parent);

        View colorCircle = view.findViewById(R.id.themeColorCircle);
        View colorContainer = view.findViewById(R.id.themeColorContainer);
        ImageView selectedIndicator = view.findViewById(R.id.selectedIndicator);
        TextView themeName = view.findViewById(R.id.themeName);

        int color = ContextCompat.getColor(ctx, colorRes[position]);
        colorCircle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));

        boolean isSelected = themes.get(position).getId() == currentThemeId;

        colorContainer.setSelected(isSelected);

        if (isSelected) {
            selectedIndicator.setVisibility(View.VISIBLE);

            themeName.setTypeface(themeName.getTypeface(), Typeface.BOLD);

            int primaryColor = MaterialColors.getColor(ctx,
                    com.google.android.material.R.attr.colorPrimaryVariant,
                    ContextCompat.getColor(ctx, android.R.color.holo_blue_bright));

            themeName.setTextColor(primaryColor);
        } else {
            selectedIndicator.setVisibility(View.GONE);
            themeName.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);

            int textColor = MaterialColors.getColor(ctx,
                    com.google.android.material.R.attr.colorOnSurface,
                    ContextCompat.getColor(ctx, android.R.color.black));

            themeName.setTextColor(textColor);
        }

        return view;
    }

    @Override
    public int getCount() {
        return Math.min(themes.size(), names.length);
    }
}
