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
            drawable.setCornerRadius(25f);

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
            GradientDrawable gradientDrawable = new GradientDrawable(
                    orientation,
                    new int[] { primaryColor, secondaryColor });
            gradientDrawable.setShape(GradientDrawable.RECTANGLE);
            gradientDrawable.setCornerRadius(25f);

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
        if (degrees < 0)
            degrees += 360;

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
     * Отримує акцентний колір для UI елементів на основі фону нотатки
     */
    public static int getAccentColor(NoteBackground background, Context context) {
        if (background == null || background.getType() == NoteBackground.BackgroundType.DEFAULT) {
            // Повертаємо стандартний primary колір теми
            return getThemePrimaryColor(context);
        }
        
        int primaryColor;
        try {
            primaryColor = Color.parseColor(background.getPrimaryColor());
        } catch (Exception e) {
            return getThemePrimaryColor(context);
        }
        
        boolean isDarkTheme = isDarkTheme(context);
        return getAccentColorFromPrimary(primaryColor, isDarkTheme);
    }
    
    /**
     * Отримує колір для прогрес-бару на основі фону нотатки
     */
    public static int getProgressBarColor(NoteBackground background, Context context) {
        if (background == null || background.getType() == NoteBackground.BackgroundType.DEFAULT) {
            return getThemePrimaryColor(context);
        }
        
        int primaryColor;
        try {
            primaryColor = Color.parseColor(background.getPrimaryColor());
        } catch (Exception e) {
            return getThemePrimaryColor(context);
        }
        
        boolean isDarkTheme = isDarkTheme(context);
        return getProgressBarColorFromPrimary(primaryColor, isDarkTheme);
    }
    
    /**
     * Перевіряє чи використовується темна тема
     */
    private static boolean isDarkTheme(Context context) {
        int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }
    
    /**
     * Отримує стандартний primary колір теми
     */
    private static int getThemePrimaryColor(Context context) {
        // Тут можна отримати колір з теми, поки що використовуємо стандартний
        return context.getResources().getColor(android.R.color.holo_blue_bright, context.getTheme());
    }
    
    /**
     * Робить колір яскравішим
     */
    private static int brightenColor(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        
        hsv[1] = Math.min(1.0f, hsv[1] * (1.0f + factor));
        hsv[2] = Math.min(1.0f, hsv[2] * (1.0f + factor));
        
        return Color.HSVToColor(hsv);
    }
    
    /**
     * Робить колір темнішим
     */
    private static int darkenColor(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        
        hsv[1] = Math.min(1.0f, hsv[1] * (1.0f + factor * 0.3f));
        hsv[2] = Math.max(0.0f, hsv[2] * (1.0f - factor));
        
        return Color.HSVToColor(hsv);
    }
    
    /**
     * Отримує акцентний колір з primary кольору
     */
    private static int getAccentColorFromPrimary(int primaryColor, boolean isDarkTheme) {
        if (isDarkTheme) {
            return brightenColor(primaryColor, 0.4f);
        } else {
            return darkenColor(primaryColor, 0.3f);
        }
    }
    
    /**
     * Отримує колір для прогрес-бару з primary кольору
     */
    private static int getProgressBarColorFromPrimary(int primaryColor, boolean isDarkTheme) {
        if (isDarkTheme) {
            return brightenColor(primaryColor, 0.6f);
        } else {
            return darkenColor(primaryColor, 0.4f);
        }
    }
    
    /**
     * Визначає, чи є колір темним
     */
    public static boolean isColorDark(int color) {
        // Обчислюємо яскравість кольору за формулою luminance
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }

}
