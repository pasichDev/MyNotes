package com.pasich.mynotes.ui.view.activity;


import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.transition.platform.MaterialFade;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.data.model.Theme;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import com.pasich.mynotes.databinding.ActivityThemeBinding;
import com.pasich.mynotes.utils.adapters.themeAdapter.ThemesAdapter;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import com.pasich.mynotes.utils.themes.ThemesArray;
import com.preference.PowerPreference;

import java.util.ArrayList;
import java.util.Objects;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ThemeActivity extends BaseActivity {

    @Inject
    PreferenceHelper mPreferenceHelper;

    public ActivityThemeBinding activityThemeBinding;
    private ThemesAdapter mAdapter;
    private int themeIdStartActivity;
    private boolean enableDynamic, themeDynamicStartActivity;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        selectTheme();
        activityThemeBinding = ActivityThemeBinding.inflate(getLayoutInflater());
        getWindow().setEnterTransition(new MaterialFade().addTarget(activityThemeBinding.activityTheme));
        getWindow().setAllowEnterTransitionOverlap(true);
        super.onCreate(savedInstanceState);
        setContentView(activityThemeBinding.getRoot());
        themeIdStartActivity = PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE);
        enableDynamic = PowerPreference.getDefaultFile().getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE);
        themeDynamicStartActivity = enableDynamic;
        setSupportActionBar(activityThemeBinding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        initializeThemes();
        initFunctions();

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(finishActivity());
            }
        });

    }

    private void initializeThemes() {
        ArrayList<Theme> themes;
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            themes = new ThemesArray().getThemes(true);
        } else {
            themes = new ThemesArray().getThemes(false);
        }
        mAdapter = new ThemesAdapter(themes, themeIdStartActivity);
    }

    private void initFunctions() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activityThemeBinding.dynamicColor.setEnabled(true);
            activityThemeBinding.dynamicColor.setChecked(PowerPreference.getDefaultFile().getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE));
        }
        activityThemeBinding.screenProtection.setChecked(PowerPreference.getDefaultFile().getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_SCREEN_PROTECTION, PreferencesConfig.ARGUMENT_DEFAULT_SCREEN_PROTECTION_VALUE));
        
        // Initialize color preview and accent card state
        updateAccentCardState(enableDynamic);
        initListeners();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            finishActivity();
        }

        return true;
    }


    private boolean finishActivity() {
        Theme mTheme = mAdapter.getSelectTheme();
        boolean enableDynamicColor = PowerPreference.getDefaultFile().getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE);

        if (themeIdStartActivity != mTheme.getId()) {
            setResult(11, new Intent().putExtra("updateThemeStyle", mAdapter.getSelectTheme().getTHEME_STYLE()));
        }
        if (themeDynamicStartActivity != enableDynamicColor) {
            setResult(11, new Intent().putExtra("updateThemeStyle", R.style.AppThemeDynamic));
        }
        supportFinishAfterTransition();
        return true;
    }

    @Override
    public void initListeners() {
        // Accent Color Card Click Listener
        activityThemeBinding.accentColorCard.setOnClickListener(v -> openAccentColorDialog());
        
        mAdapter.setSelectLabelListener(position -> {
            if (!enableDynamic) {
                Theme theme = mAdapter.getThemes().get(position);
                mAdapter.selectTheme(position);
                PowerPreference.getDefaultFile().setInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, theme.getId());
                redrawActivity(theme.getTHEME_STYLE());
            }
        });
        activityThemeBinding.dynamicColor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isChecked) {
                    redrawActivity(R.style.AppThemeDynamic);
                } else {
                    redrawActivity(new ThemesArray().getThemeStyle(PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE)));
                }
                PowerPreference.getDefaultFile().setBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, isChecked);
                enableDynamic = isChecked;
                updateAccentCardState(isChecked);
            }
        });
        
        activityThemeBinding.screenProtection.setOnCheckedChangeListener((buttonView, isChecked) -> PowerPreference.getDefaultFile().setBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_SCREEN_PROTECTION, isChecked));
    }


    @Override
    public void redrawActivity(int themeStyle) {
        super.redrawActivity(themeStyle);
        setTheme(themeStyle);
        int colorPrimary = MaterialColors.getColor(this, R.attr.colorPrimary, Color.GRAY);
        int colorOnSurface = MaterialColors.getColor(this, R.attr.colorOnSurface, Color.GRAY);
        int colorOnSurfaceVariant = MaterialColors.getColor(this, R.attr.colorOnSurfaceVariant, Color.GRAY);
        int colorSurfaceContainer = MaterialColors.getColor(this, R.attr.colorSurfaceContainer, Color.GRAY);
        
        // Background
        activityThemeBinding.activityTheme.setBackgroundColor(MaterialColors.getColor(this, android.R.attr.colorBackground, Color.GRAY));

        // Dynamic Color Card and Switch
        activityThemeBinding.dynamicColorCard.setCardBackgroundColor(colorSurfaceContainer);
        activityThemeBinding.dynamicColor.setTextColor(colorOnSurface);
        activityThemeBinding.dynamicColorDescription.setTextColor(colorOnSurfaceVariant);
        
        // Screen Protection Card and Switch  
        activityThemeBinding.screenProtectionCard.setCardBackgroundColor(colorSurfaceContainer);
        activityThemeBinding.screenProtection.setTextColor(colorOnSurface);
        activityThemeBinding.screenProtectionDescription.setTextColor(colorOnSurfaceVariant);
        
        // Accent Color Card
        activityThemeBinding.accentColorCard.setCardBackgroundColor(colorSurfaceContainer);
        activityThemeBinding.accentColorDescription.setTextColor(colorOnSurfaceVariant);

        // Update color preview
        activityThemeBinding.colorPreview.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorPrimary));

    }


    /**
     * Open accent color selection dialog
     */
    private void openAccentColorDialog() {
        if (!enableDynamic) {
            ArrayList<Theme> themes;
            int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                themes = new ThemesArray().getThemes(true);
            } else {
                themes = new ThemesArray().getThemes(false);
            }
            
            // Show a simple dialog with theme options
            showThemeSelectionDialog(themes);
        }
    }
    
    /**
     * Show theme selection dialog with color previews and names
     */
    private void showThemeSelectionDialog(ArrayList<Theme> themes) {
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        String[] themeNames = {
            getString(R.string.themeBlue),
            getString(R.string.themeGreen),
            getString(R.string.themePaleRed),
            getString(R.string.themeYellow),
            getString(R.string.themePink)
        };

        int[] themeColorResources;

        if(currentNightMode == Configuration.UI_MODE_NIGHT_YES){
            themeColorResources  = new int[]{
                    R.color.default_theme_dark_primary,
                    R.color.green_theme_dark_primary,
                    R.color.pale_pink_theme_dark_primary,
                    R.color.yellow_theme_dark_primary,
                    R.color.pink_theme_dark_primary
            };
        }else {
            themeColorResources  = new int[]{
                    R.color.default_theme_light_primary,
                    R.color.green_theme_light_primary,
                    R.color.pale_pink_theme_light_primary,
                    R.color.yellow_theme_light_primary,
                    R.color.pink_theme_light_primary
            };
        }

        int maxItems = Math.min(themes.size(), themeNames.length);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.item_theme_dialog, R.id.themeName, themeNames) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);

                View colorCircle = view.findViewById(R.id.themeColorCircle);
                if (position < themeColorResources.length && position < maxItems) {
                    int color = ContextCompat.getColor(ThemeActivity.this, themeColorResources[position]);
                    colorCircle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                }

                return view;
            }

            @Override
            public int getCount() {
                return maxItems;
            }
        };

       AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.selectAccentColor))
               .setAdapter(adapter, (dialog, which) -> {
                   if (which < themes.size()) {
                       Theme selectedTheme = themes.get(which);
                       if (mAdapter != null) {
                           mAdapter.selectTheme(which);
                       }
                       PowerPreference.getDefaultFile().setInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, selectedTheme.getId());
                       redrawActivity(selectedTheme.getTHEME_STYLE());
                   }
               })
               .setNegativeButton(getString(R.string.cancel), null)
               .show();
    }

    /**
     * Update accent color card state based on dynamic color setting
     */
    private void updateAccentCardState(boolean isDynamicEnabled) {
        if (isDynamicEnabled) {
            activityThemeBinding.accentColorCard.setAlpha(0.5f);
            activityThemeBinding.accentColorCard.setClickable(false);
        } else {
            activityThemeBinding.accentColorCard.setAlpha(1.0f);
            activityThemeBinding.accentColorCard.setClickable(true);
        }
    }

}