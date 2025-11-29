package com.pasich.mynotes.ui.view.fragment.settings;

import static com.pasich.mynotes.utils.themes.ManualRedrawSwitch.updateSwitchColors;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.slider.Slider;
import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.data.model.Theme;
import com.pasich.mynotes.databinding.FragmentInterfaceSettingsBinding;
import com.pasich.mynotes.utils.themes.ThemesArray;

import java.util.ArrayList;

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

    private static int[] getInts(int currentNightMode) {
        int[] themeColorResources;

        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            themeColorResources = new int[]{R.color.default_theme_dark_primary, R.color.green_theme_dark_theme_primary, R.color.red_pale_theme_dark_primary, R.color.yellow_theme_dark_primary, R.color.purple_theme_dark_primary, R.color.red_pale_theme_dark_primary};
        } else {
            themeColorResources = new int[]{R.color.default_theme_light_primary, R.color.green_theme_light_theme_primary, R.color.red_pale_theme_light_primary, R.color.yellow_theme_light_primary, R.color.purple_theme_light_primary, R.color.red_pale_theme_light_primary};
        }
        return themeColorResources;
    }

    private void updateFontPreview(float scale) {
        if (binding == null) return;

        float textSizeSp;

        if (scale == 0f) {
            // Auto → беремо системний розмір
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

        // ОНОВЛЮЄМО СТАН СЛАЙДЕРА ПРИ ВІДКРИТТІ
        loadFontScaleState();
    }

    private void initListeners() {
        // Theme Mode Card Click Listener
        binding.themeModeCard.setOnClickListener(v -> openThemeModeDialog());

        // Accent Color Card Click Listener
        binding.accentColorCard.setOnClickListener(v -> openAccentColorDialog());

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

        int colorPrimary = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorPrimaryFixed, Color.GRAY);
        int colorOnSurface = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurface, Color.GRAY);
        int colorOnSurfaceVariant = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        int colorSurfaceContainer = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorSurfaceContainer, Color.GRAY);
        int secondary = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorSecondaryContainer, Color.GRAY);

        // Dynamic Color Card and Switch
        binding.dynamicColorCard.setCardBackgroundColor(colorSurfaceContainer);
        binding.dynamicColor.setTextColor(colorOnSurface);
        binding.dynamicColorDescription.setTextColor(colorOnSurfaceVariant);


        // Accent Color Card
        binding.accentColorCard.setCardBackgroundColor(colorSurfaceContainer);
        binding.accentColorDescription.setTextColor(colorOnSurfaceVariant);

        // Theme Mode Card
        binding.themeModeCard.setCardBackgroundColor(colorSurfaceContainer);
        binding.currentThemeModeText.setTextColor(colorOnSurfaceVariant);

        // Update theme mode icon color
        binding.themeModeIcon.setImageTintList(ColorStateList.valueOf(colorPrimary));

        // Update color preview
        binding.colorPreview.setBackgroundTintList(ColorStateList.valueOf(colorPrimary));

        // Update switch styles with proper state colors
        updateSwitchColors(binding.dynamicColor, colorPrimary, colorOnSurfaceVariant);

        // font scale
        binding.fontSizeCard.setCardBackgroundColor(colorSurfaceContainer);
        binding.fontPreviewCard.setCardBackgroundColor(secondary);
        binding.fontPreviewText.setTextColor(colorOnSurfaceVariant);
        binding.fontSizeDescription.setTextColor(colorOnSurfaceVariant);
        binding.fontSizeTitle.setTextColor(colorOnSurfaceVariant);
        binding.fontSizeValue.setTextColor(colorOnSurfaceVariant);
        applySliderColors(binding.fontSizeSlider);
    }

    private void openAccentColorDialog() {
        if (!enableDynamic) {
            ArrayList<Theme> themes = new ThemesArray().getThemes();
            showThemeSelectionDialog(themes);
        }
    }

    private void showThemeSelectionDialog(ArrayList<Theme> themes) {
        if (getContext() == null) return;

        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        int currentThemeId = themePreferencesCache.getThemeId();

        String[] themeNames = {getString(R.string.themeBlue), getString(R.string.themeGreen), getString(R.string.themeSunset), getString(R.string.themeYellow), getString(R.string.themePurple), getString(R.string.themeCoralRed)};

        int[] themeColorResources = getInts(currentNightMode);

        int maxItems = Math.min(themes.size(), themeNames.length);
        final int[] selectedPosition = {-1};

        // Find current selected theme position
        for (int i = 0; i < themes.size() && i < maxItems; i++) {
            if (themes.get(i).getId() == currentThemeId) {
                selectedPosition[0] = i;
                break;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), R.layout.item_theme_dialog, R.id.themeName, themeNames) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);

                View colorCircle = view.findViewById(R.id.themeColorCircle);
                View colorContainer = view.findViewById(R.id.themeColorContainer);
                ImageView selectedIndicator = view.findViewById(R.id.selectedIndicator);
                TextView themeName = view.findViewById(R.id.themeName);

                if (position < themeColorResources.length && position < maxItems) {
                    int color = ContextCompat.getColor(getContext(), themeColorResources[position]);
                    colorCircle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));

                    // Set selection state
                    boolean isSelected = position == selectedPosition[0];
                    view.setSelected(isSelected);
                    colorContainer.setSelected(isSelected);

                    if (isSelected) {
                        selectedIndicator.setVisibility(View.VISIBLE);
                        themeName.setTypeface(themeName.getTypeface(), android.graphics.Typeface.BOLD);

                        // Use primary color for selected text
                        int primaryColor = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorPrimaryFixed, ContextCompat.getColor(getContext(), android.R.color.holo_blue_bright));
                        themeName.setTextColor(primaryColor);
                    } else {
                        selectedIndicator.setVisibility(View.GONE);
                        themeName.setTypeface(themeName.getTypeface(), android.graphics.Typeface.NORMAL);

                        // Use default text color
                        int textColor = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurface, ContextCompat.getColor(getContext(), android.R.color.black));
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

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.Theme_MyNotes_Dialog);
        AlertDialog dialog = builder.setTitle(getString(R.string.selectAccentColor)).setAdapter(adapter, (dialogInterface, which) -> {
            if (which < themes.size()) {
                Theme selectedTheme = themes.get(which);
                selectedPosition[0] = which;

                themePreferencesCache.setThemeId(selectedTheme.getId());
                if (getActivity() != null && getActivity() instanceof ThemeChangeListener) {
                    ((ThemeChangeListener) getActivity()).onThemeChanged(selectedTheme.getTHEME_STYLE());
                }
            }
        }).setNegativeButton(getString(R.string.cancel), null).create();

        // Customize dialog appearance
        dialog.show();

        // Make dialog corners rounded
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_rounded_background);
        }
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
                int colorPrimary = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorPrimaryFixed, Color.GRAY);
                binding.themeModeIcon.setImageTintList(ColorStateList.valueOf(colorPrimary));
            }
        }
    }

    private void openThemeModeDialog() {
        if (getContext() == null) return;

        int currentThemeMode = themePreferencesCache.getThemeMode();

        String[] themeModeNames = {getString(R.string.themeModeFollowSystem), getString(R.string.themeModeLight), getString(R.string.themeModeDark)};

        int[] themeModeIcons = {R.drawable.ic_auto_mode, R.drawable.ic_light_mode, R.drawable.ic_dark_mode};

        final int[] selectedPosition = {currentThemeMode};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), R.layout.item_theme_mode_dialog, R.id.themeModeName, themeModeNames) {
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
                        int primaryColor = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorPrimaryFixed, ContextCompat.getColor(getContext(), android.R.color.holo_blue_bright));
                        themeModeName.setTextColor(primaryColor);
                    } else {
                        selectedIndicator.setVisibility(View.GONE);
                        themeModeName.setTypeface(themeModeName.getTypeface(), android.graphics.Typeface.NORMAL);

                        // Use default text color
                        int textColor = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurface, ContextCompat.getColor(getContext(), android.R.color.black));
                        themeModeName.setTextColor(textColor);
                    }
                }

                return view;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.Theme_MyNotes_Dialog);
        AlertDialog dialog = builder.setTitle(getString(R.string.selectThemeMode)).setAdapter(adapter, (dialogInterface, which) -> {
            if (which >= 0 && which < themeModeNames.length) {
                selectedPosition[0] = which;
                themePreferencesCache.setThemeMode(which);
                updateThemeModeDisplay();
                themePreferencesCache.applyCurrentThemeMode();
            }
        }).setNegativeButton(getString(R.string.cancel), null).create();

        // Customize dialog appearance
        dialog.show();

        // Make dialog corners rounded
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_rounded_background);
        }
    }

    public void updateThemeColors() {
        applyThemeColors();
    }

    private void applySliderColors(Slider slider) {
        int colorPrimary = MaterialColors.getColor(requireContext(),
                com.google.android.material.R.attr.colorPrimaryFixed, Color.GRAY);

        int colorOnSurfaceVariant = MaterialColors.getColor(requireContext(),
                com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);

        slider.setThumbTintList(ColorStateList.valueOf(colorPrimary));
        slider.setHaloTintList(ColorStateList.valueOf(colorPrimary));
        slider.setTrackActiveTintList(ColorStateList.valueOf(colorPrimary));
        slider.setTrackInactiveTintList(ColorStateList.valueOf(colorOnSurfaceVariant));
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