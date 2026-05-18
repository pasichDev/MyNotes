package com.pasich.mynotes.base.activity;

import static com.pasich.mynotes.data.preferences.SafePreferences.raw;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.pasich.mynotes.MyApp;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.utils.constants.SnackBarInfo;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import javax.inject.Inject;

public abstract class BaseActivity extends AppCompatActivity implements BaseView {

    @Inject ThemePreferencesCache themePreferencesCache;

    @Override
    public void selectTheme() {
        themePreferencesCache.applyCurrentThemeMode();
        boolean isDynamicEnabled = themePreferencesCache.isDynamicColorEnabled();
        setTheme(
                isDynamicEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? R.style.AppThemeDynamic
                        : themePreferencesCache.getCurrentThemeStyle());
        applyScreenProtection();
    }

    @Override
    protected void attachBaseContext(Context ctx) {
        float scale;

        if (MyApp.CACHE_READY) {
            scale = MyApp.CACHE.getUiFontScale();
        } else {
            scale = MyApp.GLOBAL_FONT_SCALE;
        }

        Configuration config = new Configuration(ctx.getResources().getConfiguration());
        if (scale != 0.0f) {
            config.fontScale = scale;
        } else {
            config.fontScale = ctx.getResources().getConfiguration().fontScale;
        }
        config.densityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE;
        Context scaledContext = ctx.createConfigurationContext(config);
        super.attachBaseContext(scaledContext);
    }

    private float readUiFontScale(Context ctx) {
        return raw(ctx).getFloat(
                        PreferencesConfig.ARGUMENT_PREFERENCE_UI_SCALING,
                        PreferencesConfig.ARGUMENT_DEFAULT_UI_SCALING_VALUE);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        try {
            getWindow().setExitTransition(null);
            getWindow().setEnterTransition(null);
            getWindow().setReturnTransition(null);
            getWindow().setReenterTransition(null);
            getWindow().setSharedElementExitTransition(null);
            getWindow().setSharedElementEnterTransition(null);
            getWindow().setSharedElementReturnTransition(null);
            getWindow().setSharedElementReenterTransition(null);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private void applyScreenProtection() {
        if (themePreferencesCache.isScreenProtectionEnabled()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    public void onInfoSnack(int resID, View view, int typeInfo, int time) {
        onInfoSnack(getString(resID), view, typeInfo, time);
    }

    public void onInfoSnack(String message, View view, int typeInfo, int time) {
        Snackbar snackbar =
                Snackbar.make(
                        view == null ? findViewById(android.R.id.content) : view, message, time);

        TextView snackbarTextView =
                snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);

        if (typeInfo != SnackBarInfo.Info) {
            snackbarTextView.setTypeface(snackbarTextView.getTypeface(), Typeface.BOLD);
        }

        switch (typeInfo) {
            case SnackBarInfo.Success:
                snackbar.setBackgroundTint(
                        ContextCompat.getColor(this, R.color.successColorBackground));
                snackbar.setActionTextColor(
                        ContextCompat.getColor(this, R.color.successTextOnColor));
                break;
            case SnackBarInfo.Error:
                snackbar.setBackgroundTint(
                        MaterialColors.getColor(
                                this,
                                com.google.android.material.R.attr.colorErrorContainer,
                                Color.DKGRAY));
                snackbar.setActionTextColor(
                        MaterialColors.getColor(
                                this, com.google.android.material.R.attr.colorOnError, Color.GRAY));
                break;
        }

        snackbar.show();
    }

    protected void setupEdgeToEdgeInsets(View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(
                rootView,
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            v.getPaddingLeft(),
                            systemBars.top,
                            v.getPaddingRight(),
                            systemBars.bottom);
                    return insets;
                });
    }
}
