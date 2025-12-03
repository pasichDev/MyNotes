package com.pasich.mynotes.ui.view.fragment.settings;


import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.slider.Slider;
import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.databinding.FragmentInterfaceSettingsBinding;
import com.pasich.mynotes.ui.controllers.RedrawThemeController;
import com.pasich.mynotes.ui.view.dialogs.theme.AccentColorDialog;
import com.pasich.mynotes.ui.view.dialogs.theme.ThemeModeDialog;
import com.pasich.mynotes.utils.themes.ThemesArray;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class InterfaceSettingsFragment extends Fragment {

    private static final float PREVIEW_BASE_SP = 14f;
    private final float[] FONT_SCALES = {
            0.0f,   // Auto (system default)
            0.55f,  // XS
            0.85f,  // S
            1.0f,   // Default
            1.10f,  // L
            1.25f   // XL
    };
    private final int[] FONT_LABELS = {
            R.string.font_size_auto,
            R.string.font_size_xs,
            R.string.font_size_s,
            R.string.font_size_default,
            R.string.font_size_l,
            R.string.font_size_xl
    };
    private final Handler fontScaleDebounceHandler = new Handler();
    @Inject
    ThemePreferencesCache themePreferencesCache;
    private FragmentInterfaceSettingsBinding binding;
    private boolean enableDynamic;
    private Runnable fontScaleDebounceRunnable;

    private final Slider.OnChangeListener fontScaleListener =
            (slider, value, fromUser) -> {
                if (binding == null) return;

                int i = (int) value;
                float scale = FONT_SCALES[i];

                binding.fontSizeValue.setText(getString(FONT_LABELS[i]));
                updateFontPreview(scale);
                if (!fromUser) return;
                if (fontScaleDebounceRunnable != null) {
                    fontScaleDebounceHandler.removeCallbacks(fontScaleDebounceRunnable);
                }
                fontScaleDebounceRunnable = () -> {
                    themePreferencesCache.setUiFontScale(scale);

                    if (getActivity() instanceof ThemeChangeListener) {
                        ((ThemeChangeListener) getActivity()).onFontScaleChanged(scale);
                    }
                };
                fontScaleDebounceHandler.postDelayed(fontScaleDebounceRunnable, 350);
            };

    private void updateFontPreview(float scale) {
        if (binding == null) return;

        float textSizeSp;

        if (scale == 0f) {
            // Auto → take the system size
            float systemScale = requireContext().getResources().getConfiguration().fontScale;

            textSizeSp = PREVIEW_BASE_SP * systemScale;
        } else {
            // Custom scale
            textSizeSp = PREVIEW_BASE_SP * scale;
        }

        binding.fontPreviewText.setTextSize(textSizeSp);
    }

    private void loadFontScaleState() {
        float savedScale = themePreferencesCache.getUiFontScale();

        int index = 0;
        for (int i = 0; i < FONT_SCALES.length; i++) {
            if (Math.abs(FONT_SCALES[i] - savedScale) < 0.01f) {
                index = i;
                break;
            }
        }

        binding.fontSizeSlider.setValue(index);
        binding.fontSizeValue.setText(getString(FONT_LABELS[index]));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentInterfaceSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews();
        initListeners();
    }

    private void initViews() {
        enableDynamic = themePreferencesCache.isDynamicColorEnabled();

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.dynamicColor.setEnabled(true);
            binding.dynamicColor.setChecked(themePreferencesCache.isDynamicColorEnabled());
        }

        // Initialize theme mode
        updateThemeModeDisplay();

        // Initialize color preview and accent card state
        updateAccentCardState(enableDynamic);

        // Updating slider status upon opening
        loadFontScaleState();
    }

    private void initListeners() {
        // Theme Mode Card Click Listener
        binding.themeModeCard.setOnClickListener(v ->
                ThemeModeDialog.show(requireContext(), themePreferencesCache, mode -> {
                    updateThemeModeDisplay();
                    themePreferencesCache.applyCurrentThemeMode();
                })
        );

        // Accent Color Card Click Listener
        binding.accentColorCard.setOnClickListener(v ->
                AccentColorDialog.show(
                        requireContext(),
                        themePreferencesCache,
                        selectedTheme -> {
                            if (getActivity() instanceof ThemeChangeListener) {
                                ((ThemeChangeListener) getActivity()).onThemeChanged(selectedTheme.getTHEME_STYLE());
                            }
                        }
                )
        );

        binding.dynamicColor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isChecked) {
                    // Notify activity to change theme
                    if (getActivity() != null && getActivity() instanceof ThemeChangeListener) {
                        ((ThemeChangeListener) getActivity()).onThemeChanged(R.style.AppThemeDynamic);
                    }
                } else {
                    int themeStyle = new ThemesArray().getThemeStyle(themePreferencesCache.getThemeId());
                    if (getActivity() != null && getActivity() instanceof ThemeChangeListener) {
                        ((ThemeChangeListener) getActivity()).onThemeChanged(themeStyle);
                    }
                }
                themePreferencesCache.setDynamicColor(isChecked);
                enableDynamic = isChecked;
                updateAccentCardState(isChecked);
            }
        });

        binding.fontSizeSlider.addOnChangeListener(fontScaleListener);

    }

    private void applyThemeColors() {
        if (getContext() == null) return;

        // Dynamic Color Card and Switch
        RedrawThemeController.styleCardBlock(
                binding.dynamicColorCard,
                null,
                binding.dynamicColorDescription,
                binding.dynamicColor,
                requireContext()
        );

        // Accent Color Card
        RedrawThemeController.styleCardBlock(
                binding.accentColorCard,
                null,
                binding.accentColorDescription,
                null,
                requireContext()
        );

        // Theme Mode Card
        RedrawThemeController.styleCardBlock(
                binding.themeModeCard,
                null,
                binding.currentThemeModeText,
                null,
                requireContext()
        );


        // font scale
        RedrawThemeController.styleCardBlock(
                binding.fontSizeCard,
                binding.fontSizeTitle,
                binding.fontSizeDescription,
                null,
                requireContext()
        );
        RedrawThemeController.styleCardSecondary(binding.fontPreviewCard, requireContext());
        RedrawThemeController.styleTextVariant(binding.fontPreviewText, requireContext());
        RedrawThemeController.styleTextVariant(binding.fontSizeValue, requireContext());
        // colorPreview
        RedrawThemeController.tint(binding.colorPreview, requireContext(),
                com.google.android.material.R.attr.colorPrimaryVariant);
        // themeModeIcon
        RedrawThemeController.tint(binding.themeModeIcon, requireContext(),
                com.google.android.material.R.attr.colorPrimaryVariant);
        // font scale slider
        RedrawThemeController.styleSlider(binding.fontSizeSlider, requireContext());
    }

    private void updateAccentCardState(boolean isDynamicEnabled) {
        if (isDynamicEnabled) {
            binding.accentColorCard.setAlpha(0.5f);
            binding.accentColorCard.setClickable(false);
        } else {
            binding.accentColorCard.setAlpha(1.0f);
            binding.accentColorCard.setClickable(true);
        }
    }

    private void updateThemeModeDisplay() {
        int currentThemeMode = themePreferencesCache.getThemeMode();
        String[] themeModeNames = {getString(R.string.themeModeFollowSystem), getString(R.string.themeModeLight), getString(R.string.themeModeDark)};
        int[] themeModeIcons = {R.drawable.ic_auto_mode, R.drawable.ic_light_mode, R.drawable.ic_dark_mode};

        if (currentThemeMode >= 0 && currentThemeMode < themeModeNames.length) {
            binding.currentThemeModeText.setText(themeModeNames[currentThemeMode]);
            binding.themeModeIcon.setImageResource(themeModeIcons[currentThemeMode]);

            // Update icon color to match current theme
            if (getContext() != null) {
                int colorPrimary = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorPrimaryVariant, Color.GRAY);
                binding.themeModeIcon.setImageTintList(ColorStateList.valueOf(colorPrimary));
            }
        }
    }

    public void updateThemeColors() {
        applyThemeColors();
    }

    @Override
    public void onDestroyView() {
        if (fontScaleDebounceRunnable != null) {
            fontScaleDebounceHandler.removeCallbacks(fontScaleDebounceRunnable);
        }
        binding.fontSizeSlider.removeOnChangeListener(fontScaleListener);
        binding = null;
        super.onDestroyView();
    }


    public interface ThemeChangeListener {
        void onThemeChanged(int themeStyle);

        void onFontScaleChanged(float value);
    }
}