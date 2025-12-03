package com.pasich.mynotes.ui.view.activity;

import static com.pasich.mynotes.utils.navigation.ActivityResultKeys.EXTRA_UPDATE_THEME_STYLE;
import static com.pasich.mynotes.utils.navigation.ActivityResultKeys.RESULT_CODE_THEME_UPDATE;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

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
import com.pasich.mynotes.ui.controllers.RedrawThemeController;
import com.pasich.mynotes.ui.view.fragment.settings.InteractionSettingsFragment;
import com.pasich.mynotes.ui.view.fragment.settings.InterfaceSettingsFragment;
import com.pasich.mynotes.ui.view.fragment.settings.MediaSettingsFragment;
import com.pasich.mynotes.utils.adapters.SettingsPagerAdapter;

import java.util.Objects;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SettingsActivity extends BaseActivity implements InterfaceSettingsFragment.ThemeChangeListener {

    public ActivitySettingsBinding activitySettingsBinding;
    @Inject
    ThemePreferencesCache themePreferencesCache;
    private int themeIdStartActivity;
    private boolean themeDynamicStartActivity;
    private int themeModeStartActivity;
    private SettingsPagerAdapter pagerAdapter;
    private TabLayout tabLayout;

    private float fontScaleWasChanged = -0.2f;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectTheme();
        activitySettingsBinding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(activitySettingsBinding.getRoot());
        setupEdgeToEdgeInsets(activitySettingsBinding.getRoot());
        themeIdStartActivity = themePreferencesCache.getThemeId();
        themeDynamicStartActivity = themePreferencesCache.isDynamicColorEnabled();
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
        tabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);

        // Add margins between tabs
        tabLayout.post(() -> {
            ViewGroup tabStrip = (ViewGroup) tabLayout.getChildAt(0);
            int margin = (int) (12 * getResources().getDisplayMetrics().density);

            for (int i = 0; i < tabStrip.getChildCount(); i++) {
                View tabView = tabStrip.getChildAt(i);
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) tabView.getLayoutParams();
                params.setMargins(margin, 0, margin, 0);
                tabView.setLayoutParams(params);

                tabView.requestLayout();
            }
        });

        pagerAdapter = new SettingsPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0:
                            tab.setText(getString(R.string.interface_tab));
                            break;
                        case 1:
                            tab.setText(getString(R.string.interaction_tab));
                            break;
                        case 2:
                            tab.setText(getString(R.string.mediaTab));
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

        boolean hasChanges = false;

        int currentThemeId = themePreferencesCache.getThemeId();
        boolean enableDynamicColor = themePreferencesCache.isDynamicColorEnabled();
        int currentThemeMode = themePreferencesCache.getThemeMode();

        // Any theme changes?
        if (themeIdStartActivity != currentThemeId) {
            hasChanges = true;
        }

        if (themeDynamicStartActivity != enableDynamicColor) {
            hasChanges = true;
        }

        if (themeModeStartActivity != currentThemeMode) {
            hasChanges = true;
        }

        if (fontScaleWasChanged != -0.2f) {
            hasChanges = true;
        }

        // If anything changed — send one flag
        if (hasChanges) {
            setResult(RESULT_CODE_THEME_UPDATE, new Intent().putExtra(EXTRA_UPDATE_THEME_STYLE, true));

        }

        supportFinishAfterTransition();
        return true;
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
    public void onFontScaleChanged(float value) {
        fontScaleWasChanged = value;
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
        RedrawThemeController.styleTabs(tabLayout, this);
    }

    private void updateFragmentThemes() {
        // Update fragments when theme changes
        if (pagerAdapter != null) {
            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (fragment instanceof InterfaceSettingsFragment) {
                    ((InterfaceSettingsFragment) fragment).updateThemeColors();
                } else if (fragment instanceof InteractionSettingsFragment) {
                    ((InteractionSettingsFragment) fragment).updateThemeColors();
                } else if (fragment instanceof MediaSettingsFragment) {
                    ((MediaSettingsFragment) fragment).updateThemeColors();
                }
            }
        }
    }

}