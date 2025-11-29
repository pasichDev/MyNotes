package com.pasich.mynotes.ui.view.fragment.settings;

import static com.pasich.mynotes.utils.themes.ManualRedrawSwitch.updateSwitchColors;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
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

    @Inject
    ThemePreferencesCache themePreferencesCache;

    private FragmentInterfaceSettingsBinding binding;
    private boolean enableDynamic;

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
    }

    private void applyThemeColors() {
        if (getContext() == null) return;

        int colorPrimary = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorPrimaryFixed, Color.GRAY);
        int colorOnSurface = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurface, Color.GRAY);
        int colorOnSurfaceVariant = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        int colorSurfaceContainer = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorSurfaceContainer, Color.GRAY);

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

    private static int[] getInts(int currentNightMode) {
        int[] themeColorResources;

        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            themeColorResources = new int[]{R.color.default_theme_dark_primary, R.color.green_theme_dark_theme_primary, R.color.red_pale_theme_dark_primary, R.color.yellow_theme_dark_primary, R.color.purple_theme_dark_primary, R.color.red_pale_theme_dark_primary};
        } else {
            themeColorResources = new int[]{R.color.default_theme_light_primary, R.color.green_theme_light_theme_primary, R.color.red_pale_theme_light_primary, R.color.yellow_theme_light_primary, R.color.purple_theme_light_primary, R.color.red_pale_theme_light_primary};
        }
        return themeColorResources;
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public interface ThemeChangeListener {
        void onThemeChanged(int themeStyle);
    }
}