package com.pasich.mynotes.ui.view.activity;


import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.databinding.ActivitySettingsBinding;
import com.pasich.mynotes.ui.view.fragment.InteractionSettingsFragment;
import com.pasich.mynotes.ui.view.fragment.InterfaceSettingsFragment;
import com.pasich.mynotes.utils.adapters.SettingsPagerAdapter;
import com.pasich.mynotes.utils.themes.ThemesArray;

import java.util.Objects;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SettingsActivity extends BaseActivity implements InterfaceSettingsFragment.ThemeChangeListener {

    public ActivitySettingsBinding activitySettingsBinding;
    @Inject
    ThemePreferencesCache themePreferencesCache;
    int targetPage = 0;
    private int themeIdStartActivity;
    private boolean enableDynamic, themeDynamicStartActivity;
    private int themeModeStartActivity;
    private SettingsPagerAdapter pagerAdapter;
    private TabLayout tabLayout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        selectTheme();
        activitySettingsBinding = ActivitySettingsBinding.inflate(getLayoutInflater());
        super.onCreate(savedInstanceState);
        setContentView(activitySettingsBinding.getRoot());
        setupEdgeToEdgeInsets(activitySettingsBinding.getRoot());
        themeIdStartActivity = themePreferencesCache.getThemeId();
        enableDynamic = themePreferencesCache.isDynamicColorEnabled();
        themeDynamicStartActivity = enableDynamic;
        themeModeStartActivity = themePreferencesCache.getThemeMode();
        setSupportActionBar(activitySettingsBinding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        // Animation focus new editor
        int startIndex = getIntent().getIntExtra("startFragmentIndex", 0);
        initViewPager(startIndex);

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(finishActivity());
            }
        });

    }

    private void initViewPager(int startIndex) {
        ViewPager2 viewPager = activitySettingsBinding.viewPager;

        tabLayout = activitySettingsBinding.tabLayout;
        pagerAdapter = new SettingsPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        // Налаштування TabLayout з ViewPager2
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0:
                            tab.setText(getString(R.string.interface_tab));
                            break;
                        case 1:
                            tab.setText(getString(R.string.interaction_tab));
                            break;
                    }
                }
        ).attach();

        if (startIndex == 1) {
            activitySettingsBinding.viewPager.setCurrentItem(startIndex, true);
        }

        // Apply theme colors to tabs
        applyTabColors();
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
        int currentThemeId = themePreferencesCache.getThemeId();
        boolean enableDynamicColor = themePreferencesCache.isDynamicColorEnabled();
        int currentThemeMode = themePreferencesCache.getThemeMode();

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
    protected void onDestroy() {

        super.onDestroy();
    }

    @Override
    public void initListeners() {
        // Listeners are now handled in fragments
    }

    @Override
    public void onThemeChanged(int themeStyle) {
        redrawActivity(themeStyle);
    }


    @Override
    public void redrawActivity(int themeStyle) {
        super.redrawActivity(themeStyle);
        setTheme(themeStyle);

        // Background
        activitySettingsBinding.activitySettings.setBackgroundColor(MaterialColors.getColor(this, android.R.attr.colorBackground, Color.GRAY));

        // Apply theme colors to tabs
        applyTabColors();

        // Update fragments
        updateFragmentThemes();
    }

    private void applyTabColors() {
        int colorPrimary = MaterialColors.getColor(this, R.attr.colorPrimary, Color.GRAY);
        int colorOnSurfaceVariant = MaterialColors.getColor(this, R.attr.colorOnSurfaceVariant, Color.GRAY);

        tabLayout.setTabTextColors(colorOnSurfaceVariant, colorPrimary);
        tabLayout.setSelectedTabIndicatorColor(colorPrimary);
    }

    private void updateFragmentThemes() {
        // Update fragments when theme changes
        if (pagerAdapter != null) {
            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (fragment instanceof InterfaceSettingsFragment) {
                    ((InterfaceSettingsFragment) fragment).updateThemeColors();
                } else if (fragment instanceof InteractionSettingsFragment) {
                    ((InteractionSettingsFragment) fragment).updateThemeColors();
                }
            }
        }
    }

}