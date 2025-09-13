package com.pasich.mynotes.utils.backgrounds;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.View;

import com.pasich.mynotes.data.model.NoteBackground;

/**
 * Утилітний клас для застосування фонів до View
 */
public class BackgroundApplier {
    
    private static final String TAG = "BackgroundApplier";
    
    /**
     * Застосовує фон до View
     */
    public static void applyBackground(View view, NoteBackground background, Context context) {

        if (view == null || background == null || context == null) {
           android.util.Log.w("BackgroundApplier", "Null parameter detected");
            return;
        }

        switch (background.getType()) {
            case COLOR:
               applyColorBackground(view, background.getPrimaryColor());
                break;
            case GRADIENT:
              applyGradientBackground(view, background);
                break;
            default:
               applyDefaultBackground(view);
                break;
        }

    }

    /**
     * Застосовує стандартний фон додатку
     */
    private static void applyDefaultBackground(View view) {
        view.setBackgroundColor(Color.TRANSPARENT);
    }
    
    /**
     * Застосовує однотонний кольоровий фон
     */
    private static void applyColorBackground(View view, String colorHex) {
        try {
            int color = Color.parseColor(colorHex);
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setColor(color);
            drawable.setCornerRadius(16f);

            view.setBackground(drawable);
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply color background: " + e.getMessage(), e);
            applyDefaultBackground(view);
        }
    }
    
    /**
     * Застосовує градієнтний фон
     */
    private static void applyGradientBackground(View view, NoteBackground background) {
        try {
            int primaryColor = Color.parseColor(background.getPrimaryColor());
            int secondaryColor = Color.parseColor(background.getSecondaryColor());
            
            GradientDrawable.Orientation orientation = getGradientOrientation(background.getGradientDirection());
            Log.d(TAG, "Gradient orientation: " + orientation + " (direction: " + background.getGradientDirection() + ")");
            
            GradientDrawable gradientDrawable = new GradientDrawable(
                orientation,
                new int[]{primaryColor, secondaryColor}
            );
            gradientDrawable.setShape(GradientDrawable.RECTANGLE);
            gradientDrawable.setCornerRadius(16f);
            
            view.setBackground(gradientDrawable);
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply gradient background: " + e.getMessage(), e);
            applyDefaultBackground(view);
        }
    }
    
    /**
     * Конвертує градуси в GradientDrawable.Orientation
     */
    private static GradientDrawable.Orientation getGradientOrientation(int degrees) {
        degrees = degrees % 360;
        if (degrees < 0) degrees += 360;
        
        if (degrees >= 337.5 || degrees < 22.5) {
            return GradientDrawable.Orientation.LEFT_RIGHT;
        } else if (degrees >= 22.5 && degrees < 67.5) {
            return GradientDrawable.Orientation.BL_TR;
        } else if (degrees >= 67.5 && degrees < 112.5) {
            return GradientDrawable.Orientation.BOTTOM_TOP;
        } else if (degrees >= 112.5 && degrees < 157.5) {
            return GradientDrawable.Orientation.BR_TL;
        } else if (degrees >= 157.5 && degrees < 202.5) {
            return GradientDrawable.Orientation.RIGHT_LEFT;
        } else if (degrees >= 202.5 && degrees < 247.5) {
            return GradientDrawable.Orientation.TR_BL;
        } else if (degrees >= 247.5 && degrees < 292.5) {
            return GradientDrawable.Orientation.TOP_BOTTOM;
        } else {
            return GradientDrawable.Orientation.TL_BR;
        }
    }
    
    /**
     * Перевіряє, чи використовується темна тема
     */
    private static boolean isDarkTheme(Context context) {
        int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }
    
    /**
     * Перевіряє, чи є колір темним
     */
    private static boolean isColorDark(String colorHex) {
        try {
            int color = Color.parseColor(colorHex);
            
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            
            double luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255;
            
            return luminance < 0.5;
        } catch (Exception e) {
            return false;
        }
    }

}
