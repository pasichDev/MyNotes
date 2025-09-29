package com.pasich.mynotes.utils.themes;

import android.content.res.ColorStateList;
import android.graphics.Color;

import com.google.android.material.materialswitch.MaterialSwitch;

public class ManualRedrawSwitch {

    public static void updateSwitchColors(MaterialSwitch materialSwitch, int primaryColor, int surfaceVariantColor) {
        // Create ColorStateList for thumb (the circle part)
        int[][] thumbStates = new int[][]{
                new int[]{android.R.attr.state_checked},  // checked state
                new int[]{-android.R.attr.state_checked}  // unchecked state
        };
        int[] thumbColors = new int[]{
                primaryColor,        // checked color
                Color.WHITE          // unchecked color (white thumb)
        };
        ColorStateList thumbColorStateList = new ColorStateList(thumbStates, thumbColors);

        // Create ColorStateList for track (the background)
        int[][] trackStates = new int[][]{
                new int[]{android.R.attr.state_checked},  // checked state
                new int[]{-android.R.attr.state_checked}  // unchecked state
        };
        int[] trackColors = new int[]{
                adjustAlpha(primaryColor, 0.5f),  // checked color with transparency
                adjustAlpha(surfaceVariantColor, 0.3f)  // unchecked color with transparency
        };
        ColorStateList trackColorStateList = new ColorStateList(trackStates, trackColors);

        // Apply the color state lists
        materialSwitch.setThumbTintList(thumbColorStateList);
        materialSwitch.setTrackTintList(trackColorStateList);
    }


    public static int adjustAlpha(int color, float alpha) {
        int alphaValue = Math.round(Color.alpha(color) * alpha);
        return Color.argb(alphaValue, Color.red(color), Color.green(color), Color.blue(color));
    }

}
