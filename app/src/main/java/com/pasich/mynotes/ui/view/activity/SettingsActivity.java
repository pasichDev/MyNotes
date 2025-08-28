package com.pasich.mynotes.ui.view.activity;


import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.transition.platform.MaterialFade;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.data.model.Theme;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import com.pasich.mynotes.databinding.ActivitySettingsBinding;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import com.pasich.mynotes.utils.themes.ThemesArray;
import com.preference.PowerPreference;

import java.util.ArrayList;
import java.util.Objects;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SettingsActivity extends BaseActivity {

    @Inject
    PreferenceHelper mPreferenceHelper;

    public ActivitySettingsBinding activitySettingsBinding;
    private int themeIdStartActivity;
    private boolean enableDynamic, themeDynamicStartActivity;
    private int themeModeStartActivity;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        selectTheme();
        activitySettingsBinding = ActivitySettingsBinding.inflate(getLayoutInflater());
        getWindow().setEnterTransition(new MaterialFade().addTarget(activitySettingsBinding.activitySettings));
        getWindow().setAllowEnterTransitionOverlap(true);
        super.onCreate(savedInstanceState);
        setContentView(activitySettingsBinding.getRoot());
        setupEdgeToEdgeInsets(activitySettingsBinding.getRoot());
        themeIdStartActivity = PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE);
        enableDynamic = PowerPreference.getDefaultFile().getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE);
        themeDynamicStartActivity = enableDynamic;
        themeModeStartActivity = PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE);
        setSupportActionBar(activitySettingsBinding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        initFunctions();

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(finishActivity());
            }
        });

    }

    private void initFunctions() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activitySettingsBinding.dynamicColor.setEnabled(true);
            activitySettingsBinding.dynamicColor.setChecked(PowerPreference.getDefaultFile().getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE));
        }
        activitySettingsBinding.screenProtection.setChecked(PowerPreference.getDefaultFile().getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_SCREEN_PROTECTION, PreferencesConfig.ARGUMENT_DEFAULT_SCREEN_PROTECTION_VALUE));
        
        // Initialize theme mode
        updateThemeModeDisplay();
        
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
        int currentThemeId = PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, PreferencesConfig.ARGUMENT_DEFAULT_THEME_VALUE);
        boolean enableDynamicColor = PowerPreference.getDefaultFile().getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE);
        int currentThemeMode = PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE);
        
        if (themeIdStartActivity != currentThemeId) {
            int themeStyle = new ThemesArray().getThemeStyle(currentThemeId);
            setResult(11, new Intent().putExtra("updateThemeStyle", themeStyle));
        }

        if (themeDynamicStartActivity != enableDynamicColor) {
            if (enableDynamicColor) {
                setResult(11, new Intent().putExtra("updateThemeStyle", R.style.AppThemeDynamic));
            } else {
                int themeStyle = new ThemesArray().getThemeStyle(currentThemeId);
                setResult(11, new Intent().putExtra("updateThemeStyle", themeStyle));
            }
        }
        
        if (themeModeStartActivity != currentThemeMode) {
            // Theme mode changed, trigger recreation
            setResult(11, new Intent().putExtra("updateThemeMode", true));
        }

        supportFinishAfterTransition();
        return true;
    }

    @Override
    public void initListeners() {
        // Theme Mode Card Click Listener
        activitySettingsBinding.themeModeCard.setOnClickListener(v -> openThemeModeDialog());
        
        // Accent Color Card Click Listener
        activitySettingsBinding.accentColorCard.setOnClickListener(v -> openAccentColorDialog());
        activitySettingsBinding.dynamicColor.setOnCheckedChangeListener((buttonView, isChecked) -> {
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
        
        activitySettingsBinding.screenProtection.setOnCheckedChangeListener((buttonView, isChecked) -> PowerPreference.getDefaultFile().setBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_SCREEN_PROTECTION, isChecked));
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
        activitySettingsBinding.activitySettings.setBackgroundColor(MaterialColors.getColor(this, android.R.attr.colorBackground, Color.GRAY));

        // Dynamic Color Card and Switch
        activitySettingsBinding.dynamicColorCard.setCardBackgroundColor(colorSurfaceContainer);
        activitySettingsBinding.dynamicColor.setTextColor(colorOnSurface);
        activitySettingsBinding.dynamicColorDescription.setTextColor(colorOnSurfaceVariant);
        
        // Screen Protection Card and Switch  
        activitySettingsBinding.screenProtectionCard.setCardBackgroundColor(colorSurfaceContainer);
        activitySettingsBinding.screenProtection.setTextColor(colorOnSurface);
        activitySettingsBinding.screenProtectionDescription.setTextColor(colorOnSurfaceVariant);
        
        // Accent Color Card
        activitySettingsBinding.accentColorCard.setCardBackgroundColor(colorSurfaceContainer);
        activitySettingsBinding.accentColorDescription.setTextColor(colorOnSurfaceVariant);

        // Theme Mode Card
        activitySettingsBinding.themeModeCard.setCardBackgroundColor(colorSurfaceContainer);
        activitySettingsBinding.currentThemeModeText.setTextColor(colorOnSurfaceVariant);
        
        // Update theme mode icon color
        activitySettingsBinding.themeModeIcon.setImageTintList(ColorStateList.valueOf(colorPrimary));

        // Update color preview
        activitySettingsBinding.colorPreview.setBackgroundTintList(ColorStateList.valueOf(colorPrimary));

        // Update switch styles with proper state colors
        updateSwitchColors(activitySettingsBinding.dynamicColor, colorPrimary, colorOnSurfaceVariant);
        updateSwitchColors(activitySettingsBinding.screenProtection, colorPrimary, colorOnSurfaceVariant);

    }

    /**
     * Update switch colors for different states
     */
    private void updateSwitchColors(MaterialSwitch materialSwitch, int primaryColor, int surfaceVariantColor) {
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

    /**
     * Adjust color alpha
     */
    private int adjustAlpha(int color, float alpha) {
        int alphaValue = Math.round(Color.alpha(color) * alpha);
        return Color.argb(alphaValue, Color.red(color), Color.green(color), Color.blue(color));
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
        int currentThemeId = PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, 0);

        String[] themeNames = {
            getString(R.string.themeBlue),
            getString(R.string.themeGreen),
            getString(R.string.themeSunset),
            getString(R.string.themeYellow),
            getString(R.string.themePurple),
            getString(R.string.themeCoralRed)
        };

        int[] themeColorResources;

        if(currentNightMode == Configuration.UI_MODE_NIGHT_YES){
            themeColorResources  = new int[]{
                    R.color.default_theme_dark_primary,
                    R.color.green_theme_dark_theme_primary,
                    R.color.red_pale_theme_dark_primary,
                    R.color.yellow_theme_dark_primary,
                    R.color.purple_theme_dark_primary,
                    R.color.red_pale_theme_dark_primary
            };
        }else {
            themeColorResources  = new int[]{
                    R.color.default_theme_light_primary,
                    R.color.green_theme_light_theme_primary,
                    R.color.red_pale_theme_light_primary,
                    R.color.yellow_theme_light_primary,
                    R.color.purple_theme_light_primary,
                    R.color.red_pale_theme_light_primary
            };
        }

        int maxItems = Math.min(themes.size(), themeNames.length);
        final int[] selectedPosition = {-1};
        
        // Find current selected theme position
        for (int i = 0; i < themes.size() && i < maxItems; i++) {
            if (themes.get(i).getId() == currentThemeId) {
                selectedPosition[0] = i;
                break;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.item_theme_dialog, R.id.themeName, themeNames) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);

                View colorCircle = view.findViewById(R.id.themeColorCircle);
                View colorContainer = view.findViewById(R.id.themeColorContainer);
                ImageView selectedIndicator = view.findViewById(R.id.selectedIndicator);
                TextView themeName = view.findViewById(R.id.themeName);
                
                if (position < themeColorResources.length && position < maxItems) {
                    int color = ContextCompat.getColor(SettingsActivity.this, themeColorResources[position]);
                    colorCircle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                    
                    // Set selection state
                    boolean isSelected = position == selectedPosition[0];
                    view.setSelected(isSelected);
                    colorContainer.setSelected(isSelected);
                    
                    if (isSelected) {
                        selectedIndicator.setVisibility(View.VISIBLE);
                        themeName.setTypeface(themeName.getTypeface(), android.graphics.Typeface.BOLD);
                        
                        // Use primary color for selected text
                        int primaryColor = MaterialColors.getColor(SettingsActivity.this, 
                            R.attr.colorPrimary,
                            ContextCompat.getColor(SettingsActivity.this, android.R.color.holo_blue_bright));
                        themeName.setTextColor(primaryColor);
                    } else {
                        selectedIndicator.setVisibility(View.GONE);
                        themeName.setTypeface(themeName.getTypeface(), android.graphics.Typeface.NORMAL);
                        
                        // Use default text color
                        int textColor = MaterialColors.getColor(SettingsActivity.this,
                            R.attr.colorOnSurface,
                            ContextCompat.getColor(SettingsActivity.this, android.R.color.black));
                        themeName.setTextColor(textColor);
                    }
                }

                return view;
            }

            @Override
            public int getCount() {
                return maxItems;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_MyNotes_Dialog);
        AlertDialog dialog = builder.setTitle(getString(R.string.selectAccentColor))
               .setAdapter(adapter, (dialogInterface, which) -> {
                   if (which < themes.size()) {
                       Theme selectedTheme = themes.get(which);
                       selectedPosition[0] = which;

                       PowerPreference.getDefaultFile().setInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME, selectedTheme.getId());
                       redrawActivity(selectedTheme.getTHEME_STYLE());
                   }
               })
               .setNegativeButton(getString(R.string.cancel), null)
               .create();
               
        // Customize dialog appearance
        dialog.show();
        
        // Make dialog corners rounded
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_rounded_background);
        }
    }

    /**
     * Update accent color card state based on dynamic color setting
     */
    private void updateAccentCardState(boolean isDynamicEnabled) {
        if (isDynamicEnabled) {
            activitySettingsBinding.accentColorCard.setAlpha(0.5f);
            activitySettingsBinding.accentColorCard.setClickable(false);
        } else {
            activitySettingsBinding.accentColorCard.setAlpha(1.0f);
            activitySettingsBinding.accentColorCard.setClickable(true);
        }
    }

    /**
     * Update theme mode display text and icon
     */
    private void updateThemeModeDisplay() {
        int currentThemeMode = PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE);
        
        String[] themeModeNames = {
            getString(R.string.themeModeFollowSystem),
            getString(R.string.themeModeLight),
            getString(R.string.themeModeDark)
        };
        
        int[] themeModeIcons = {
            R.drawable.ic_auto_mode,
            R.drawable.ic_light_mode,
            R.drawable.ic_dark_mode
        };
        
        if (currentThemeMode >= 0 && currentThemeMode < themeModeNames.length) {
            activitySettingsBinding.currentThemeModeText.setText(themeModeNames[currentThemeMode]);
            activitySettingsBinding.themeModeIcon.setImageResource(themeModeIcons[currentThemeMode]);
            
            // Update icon color to match current theme
            int colorPrimary = MaterialColors.getColor(this, R.attr.colorPrimary, Color.GRAY);
            activitySettingsBinding.themeModeIcon.setImageTintList(ColorStateList.valueOf(colorPrimary));
        }
    }

    /**
     * Open theme mode selection dialog
     */
    private void openThemeModeDialog() {
        int currentThemeMode = PowerPreference.getDefaultFile().getInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, PreferencesConfig.ARGUMENT_DEFAULT_THEME_MODE_VALUE);
        
        String[] themeModeNames = {
            getString(R.string.themeModeFollowSystem),
            getString(R.string.themeModeLight),
            getString(R.string.themeModeDark)
        };
        
        int[] themeModeIcons = {
            R.drawable.ic_auto_mode,
            R.drawable.ic_light_mode,
            R.drawable.ic_dark_mode
        };

        final int[] selectedPosition = {currentThemeMode};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.item_theme_mode_dialog, R.id.themeModeName, themeModeNames) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);

                ImageView themeModeIcon = view.findViewById(R.id.themeModeIcon);
                ImageView selectedIndicator = view.findViewById(R.id.selectedModeIndicator);
                TextView themeModeName = view.findViewById(R.id.themeModeName);
                
                if (position < themeModeIcons.length) {
                    themeModeIcon.setImageResource(themeModeIcons[position]);
                    
                    // Set selection state
                    boolean isSelected = position == selectedPosition[0];
                    view.setSelected(isSelected);
                    
                    if (isSelected) {
                        selectedIndicator.setVisibility(View.VISIBLE);
                        themeModeName.setTypeface(themeModeName.getTypeface(), android.graphics.Typeface.BOLD);
                        
                        // Use primary color for selected text
                        int primaryColor = MaterialColors.getColor(SettingsActivity.this, 
                            R.attr.colorPrimary,
                            ContextCompat.getColor(SettingsActivity.this, android.R.color.holo_blue_bright));
                        themeModeName.setTextColor(primaryColor);
                    } else {
                        selectedIndicator.setVisibility(View.GONE);
                        themeModeName.setTypeface(themeModeName.getTypeface(), android.graphics.Typeface.NORMAL);
                        
                        // Use default text color
                        int textColor = MaterialColors.getColor(SettingsActivity.this,
                            R.attr.colorOnSurface,
                            ContextCompat.getColor(SettingsActivity.this, android.R.color.black));
                        themeModeName.setTextColor(textColor);
                    }
                }

                return view;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_MyNotes_Dialog);
        AlertDialog dialog = builder.setTitle(getString(R.string.selectThemeMode))
               .setAdapter(adapter, (dialogInterface, which) -> {
                   if (which >= 0 && which < themeModeNames.length) {
                       selectedPosition[0] = which;
                       PowerPreference.getDefaultFile().setInt(PreferencesConfig.ARGUMENT_PREFERENCE_THEME_MODE, which);
                       updateThemeModeDisplay();
                       applyThemeMode(which);
                   }
               })
               .setNegativeButton(getString(R.string.cancel), null)
               .create();
               
        // Customize dialog appearance
        dialog.show();
        
        // Make dialog corners rounded
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_rounded_background);
        }
    }

    /**
     * Apply theme mode and redraw activity
     */
    private void applyThemeMode(int themeMode) {
        // First, apply the theme mode setting to AppCompatDelegate
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
        
        // Force recreate the activity to apply the new night mode immediately
        recreate();
    }

}