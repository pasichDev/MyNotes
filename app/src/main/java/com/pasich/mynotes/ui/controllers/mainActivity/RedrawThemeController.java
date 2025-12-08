package com.pasich.mynotes.ui.controllers.mainActivity;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;

public class RedrawThemeController {

    // Card backgrounds
    public static void styleCard(MaterialCardView card, Context ctx) {
        int bg = MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorSurfaceContainer, Color.GRAY);
        card.setCardBackgroundColor(bg);
    }

    public static void styleCardSecondary(MaterialCardView card, Context ctx) {
        int bg = MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorSecondaryContainer, Color.GRAY);
        card.setCardBackgroundColor(bg);
    }

    // Text colors
    public static void styleText(TextView tv, Context ctx) {
        tv.setTextColor(MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorOnSurface, Color.GRAY));
    }

    public static void styleTextVariant(TextView tv, Context ctx) {
        tv.setTextColor(MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
    }

    // Switch styling
    public static void styleSwitch(MaterialSwitch sw, Context ctx) {
        int active = MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorPrimaryFixed, Color.GRAY);
        int inactive = MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);

        updateSwitchColors(sw, active, inactive);
    }

    // Slider styling
    public static void styleSlider(@NonNull Slider slider, @NonNull Context ctx) {
        int active = MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorPrimaryVariant, Color.GRAY);

        int inactive = MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorSurfaceVariant,
                MaterialColors.getColor(ctx,
                        com.google.android.material.R.attr.colorOutlineVariant,
                        Color.GRAY));

        slider.setThumbTintList(ColorStateList.valueOf(active));
        slider.setHaloTintList(ColorStateList.valueOf(active));
        slider.setTrackActiveTintList(ColorStateList.valueOf(active));
        slider.setTrackInactiveTintList(ColorStateList.valueOf(inactive));
    }

    // Combined card block
    public static void styleCardBlock(
            MaterialCardView card,
            @Nullable TextView title,
            @Nullable TextView description,
            @Nullable MaterialSwitch sw,
            Context ctx
    ) {
        styleCard(card, ctx);
        if (title != null) styleText(title, ctx);
        if (description != null) styleTextVariant(description, ctx);
        if (sw != null) styleSwitch(sw, ctx);
    }

    // Background tinting
    public static void tint(@NonNull View view, @NonNull Context ctx, int colorAttr) {
        int color = MaterialColors.getColor(ctx, colorAttr, Color.GRAY);
        ColorStateList tint = ColorStateList.valueOf(color);

        if (view instanceof MaterialCardView) {
            ((MaterialCardView) view).setCardBackgroundColor(color);
            return;
        }

        if (view instanceof ImageView) {
            ((ImageView) view).setImageTintList(tint);
            return;
        }

        view.setBackgroundTintList(tint);
    }

    // TabLayout styling
    public static void styleTabs(@NonNull TabLayout tabs, @NonNull Context ctx) {
        int primary = MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorPrimaryVariant, Color.GRAY);

        int textInactive = MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);

        int container = MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorSurface, Color.GRAY);

        tabs.setTabTextColors(textInactive, primary);
        tabs.setSelectedTabIndicatorColor(primary);
        tabs.setBackgroundColor(container);
    }

    // Internal switch coloring
    private static void updateSwitchColors(MaterialSwitch sw, int primary, int surfaceVariant) {
        int[][] thumbStates = {
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        int[] thumbColors = {
                primary,
                Color.WHITE
        };
        sw.setThumbTintList(new ColorStateList(thumbStates, thumbColors));

        int[][] trackStates = {
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        int[] trackColors = {
                adjustAlpha(primary, 0.5f),
                adjustAlpha(surfaceVariant, 0.3f)
        };
        sw.setTrackTintList(new ColorStateList(trackStates, trackColors));
    }

    private static int adjustAlpha(int color, float alpha) {
        int a = Math.round(Color.alpha(color) * alpha);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}

