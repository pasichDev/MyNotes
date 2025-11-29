package com.pasich.mynotes.ui.view.fragment.settings;

import static com.pasich.mynotes.utils.themes.ManualRedrawSwitch.updateSwitchColors;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.color.MaterialColors;
import com.pasich.mynotes.cache.AppPreferencesCache;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.databinding.FragmentMediaSettingsBinding;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MediaSettingsFragment extends Fragment {

    @Inject
    ThemePreferencesCache themePreferencesCache;

    @Inject
    AppPreferencesCache appPreferencesCache;

    private FragmentMediaSettingsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMediaSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews();
        initListeners();

        updateMemoryUsage();
    }

    private void initViews() {
        binding.imgOptSwitch.setChecked(appPreferencesCache.getImageOpt());
        binding.setExtendedDetailsVisible(false);
    }

    private void initListeners() {
        binding.imgOptSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                appPreferencesCache.setImageOpt(isChecked));

    }

    private void applyThemeColors() {
        if (getContext() == null) return;
        // Apply theme colors to views
        int colorSurfaceContainer = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorSurfaceContainer, Color.GRAY);
        int colorOnSurface = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurface, Color.GRAY);
        int colorOnSurfaceVariant = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        int colorPrimary = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorPrimaryFixed, Color.GRAY);


        // imgOptSwitch Card and Switch
        binding.imgOpt.setCardBackgroundColor(colorSurfaceContainer);
        binding.imgOptSwitch.setTextColor(colorOnSurface);
        binding.imgOptDescription.setTextColor(colorOnSurfaceVariant);


        updateSwitchColors(binding.imgOptSwitch, colorPrimary, colorOnSurfaceVariant);
    }


    public void updateThemeColors() {
        applyThemeColors();
    }

    private void updateMemoryUsage() {
        if (getContext() == null) return;
        long usedBytes = AttachmentStorage.getTotalAttachmentsSize(getContext());
        float usedMB = usedBytes / 1024f / 1024f;
        binding.memoryValue.setText(String.format("%.1f MB", usedMB));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;

    }
}