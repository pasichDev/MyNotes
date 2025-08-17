package com.pasich.mynotes.ui.view.activity;


import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.transition.platform.MaterialFade;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.data.model.Theme;
import com.pasich.mynotes.databinding.ActivityThemeBinding;
import com.pasich.mynotes.utils.adapters.themeAdapter.ThemesAdapter;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import com.pasich.mynotes.utils.recycler.SpacesItemDecoration;
import com.pasich.mynotes.utils.themes.ThemesArray;
import com.preference.PowerPreference;

import java.util.ArrayList;
import java.util.Objects;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ThemeActivity extends BaseActivity {


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
        setListThemes();
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
            activityThemeBinding.dynamicColor.setEnabled(true);
            activityThemeBinding.dynamicColor.setChecked(PowerPreference.getDefaultFile().getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_DYNAMIC_COLOR, PreferencesConfig.ARGUMENT_DEFAULT_DYNAMIC_COLOR_VALUE));
        }
    }

    private void setListThemes() {
        ArrayList<Theme> themes;

        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            themes = new ThemesArray().getThemes(true);
        } else {
            themes = new ThemesArray().getThemes(false);
        }
        mAdapter = new ThemesAdapter(themes, themeIdStartActivity);
        activityThemeBinding.themes.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        activityThemeBinding.themes.addItemDecoration(new SpacesItemDecoration(10));
        activityThemeBinding.themes.setAdapter(mAdapter);
        initListeners();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_toolbar, menu);
        return true;
    }

    @Override
    public void onBackPressed() {
        finishActivity();
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
                setStatusDynamicColor(isChecked);
            }
        });
    }


    @Override
    public void redrawActivity(int themeStyle) {
        super.redrawActivity(themeStyle);
        setTheme(themeStyle);
        int colorOnBackground = MaterialColors.getColor(this, R.attr.colorOnBackground, Color.GRAY);
        activityThemeBinding.activityTheme.setBackgroundColor(MaterialColors.getColor(this, android.R.attr.colorBackground, Color.GRAY));
        activityThemeBinding.titleTheme.setTextColor(colorOnBackground);

        // materialSwitch
        activityThemeBinding.dynamicColor.setTrackTintList(ColorStateList.valueOf(MaterialColors.getColor(this, R.attr.colorSurfaceVariant, Color.GRAY)));
        activityThemeBinding.dynamicColor.setThumbTintList(ColorStateList.valueOf(MaterialColors.getColor(this, R.attr.colorPrimary, Color.GRAY)));
    }


    /**
     * Set enable/disable list themes depending on whether dynamic color is enabled
     *
     * @param status - dynamic color status
     */
    private void setStatusDynamicColor(boolean status) {
        if (status) {
            activityThemeBinding.themes.setAlpha(0.4f);
            activityThemeBinding.themes.setLayoutFrozen(true);
        } else {
            activityThemeBinding.themes.setAlpha(1.0f);
            activityThemeBinding.themes.setLayoutFrozen(false);
        }
    }
}