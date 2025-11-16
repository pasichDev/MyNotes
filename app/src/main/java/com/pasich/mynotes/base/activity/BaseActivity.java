package com.pasich.mynotes.base.activity;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.utils.constants.SnackBarInfo;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import com.pasich.mynotes.utils.themes.ThemesArray;
import com.preference.PowerPreference;

import javax.inject.Inject;

public abstract class BaseActivity extends AppCompatActivity implements BaseView {

    @Inject
    ThemePreferencesCache themePreferencesCache;

    @Override
    public void selectTheme() {
        // Безпечний виклик applyCurrentThemeMode з fallback
        if (themePreferencesCache != null) {
            themePreferencesCache.applyCurrentThemeMode();
        } else {
            // Fallback для раннього виклику до ініціалізації DI
            applyThemeMode();
        }

        // Безпечна перевірка для ранніх викликів до ініціалізації DI
        boolean isDynamicEnabled = (themePreferencesCache != null) ?
                themePreferencesCache.isDynamicColorEnabled() :
                PowerPreference.getDefaultFile().getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE);

        setTheme(isDynamicEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                R.style.AppThemeDynamic : getSelectedTheme());
        applyScreenProtection();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        // Clear exit transition coordinator
        try {
            getWindow().setExitTransition(null);
            getWindow().setEnterTransition(null);
            getWindow().setReturnTransition(null);
            getWindow().setReenterTransition(null);
            getWindow().setSharedElementExitTransition(null);
            getWindow().setSharedElementEnterTransition(null);
            getWindow().setSharedElementReturnTransition(null);
            getWindow().setSharedElementReenterTransition(null);
        } catch (Exception e) {
            // Ignore exceptions during cleanup
        }
        super.onDestroy();
    }

    private void applyScreenProtection() {
        // Безпечна перевірка для ранніх викликів до ініціалізації DI
        boolean isScreenProtectionEnabled = (themePreferencesCache != null) ?
                themePreferencesCache.isScreenProtectionEnabled() :
                PowerPreference.getDefaultFile().getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_SCREEN_PROTECTION, PreferencesConfig.ARGUMENT_DEFAULT_SCREEN_PROTECTION_VALUE);

        if (isScreenProtectionEnabled) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    /**
     * Apply theme mode (light/dark/system)
     */
    private void applyThemeMode() {
        // Безпечна перевірка для ранніх викликів до ініціалізації DI
        int themeMode = (themePreferencesCache != null) ?
                themePreferencesCache.getThemeMode() :
                PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE);

        switch (themeMode) {
            case 0: // Follow System
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case 1: // Light
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case 2: // Dark
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    private int getSelectedTheme() {
        // Безпечна перевірка для ранніх викликів до ініціалізації DI
        int themeId = (themePreferencesCache != null) ?
                themePreferencesCache.getThemeId() :
                PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE);

        return new ThemesArray().getThemeStyle(themeId);
    }

    // Метод для ресурсов
    public void onInfoSnack(int resID, View view, int typeInfo, int time) {
        onInfoSnack(getString(resID), view, typeInfo, time);
    }

    // Метод для готового рядка
    public void onInfoSnack(String message, View view, int typeInfo, int time) {
        Snackbar snackbar = Snackbar.make(
                view == null ? findViewById(android.R.id.content) : view,
                message,
                time
        );

        if (typeInfo != SnackBarInfo.Info) {
            TextView snackbarTextView = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
            snackbarTextView.setTypeface(snackbarTextView.getTypeface(), Typeface.BOLD);
        }

        switch (typeInfo) {
            case SnackBarInfo.Info:
                break;
            case SnackBarInfo.Success:
                snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.successColorBackground));
                snackbar.setActionTextColor(ContextCompat.getColor(this, R.color.successTextOnColor));
                break;
            case SnackBarInfo.Error:
                snackbar.setBackgroundTint(MaterialColors.getColor(this, com.google.android.material.R.attr.colorErrorContainer, Color.DKGRAY));
                snackbar.setActionTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnError, Color.GRAY));
                break;
            default:
        }

        snackbar.show();
    }

    /**
     * Налаштовує відступи для кореневого view з урахуванням системних барів
     * Викликайте цей метод після setContentView() у дочірніх Activity
     */
    protected void setupEdgeToEdgeInsets(View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {

            // Отримуємо відступи для системних барів
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
            );

            // Встановлюємо padding тільки зверху та знизу
            v.setPadding(
                    v.getPaddingLeft(),
                    systemBars.top,
                    v.getPaddingRight(),
                    systemBars.bottom
            );

            return insets;
        });
    }

}
