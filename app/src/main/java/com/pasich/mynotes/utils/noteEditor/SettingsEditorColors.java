package com.pasich.mynotes.utils.noteEditor;

import android.content.Context;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.Map;

public class SettingsEditorColors {

    public Map<String, String> getThemeColors(Context context) {
        Map<String, String> colors = new HashMap<>();
        colors.put("surface-color", resolveAttrColor(context, com.google.android.material.R.attr.colorSurfaceContainerLow));
        colors.put("on-surface-color", resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurface));
        colors.put("primary-color", resolveAttrColor(context, com.google.android.material.R.attr.colorPrimaryFixed));
        colors.put("on-primary-color", resolveAttrColor(context, com.google.android.material.R.attr.colorOnPrimary));
        colors.put("surface-variant-color", resolveAttrColor(context, com.google.android.material.R.attr.colorSurfaceVariant));
        colors.put("on-surface-variant-color", resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
        colors.put("outline-color", resolveAttrColor(context, com.google.android.material.R.attr.colorOutline));

        return colors;
    }


    private String resolveAttrColor(Context context, int attrRes) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(attrRes, typedValue, true)) {
            int color;
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                color = typedValue.data;
            } else {
                color = ContextCompat.getColor(context, typedValue.resourceId);
            }
            return String.format("#%06X", (0xFFFFFF & color));
        } else {
            return "#000000"; // fallback
        }
    }
}
