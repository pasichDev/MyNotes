package com.pasich.mynotes.ui.view.fragment;

import static com.pasich.mynotes.utils.constants.ContactLink.SEND_FEEDBACK_EDITOR;
import static com.pasich.mynotes.utils.themes.ManualRedrawSwitch.updateSwitchColors;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.color.MaterialColors;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.databinding.FragmentInteractionSettingsBinding;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class InteractionSettingsFragment extends Fragment {

    @Inject
    ThemePreferencesCache themePreferencesCache;

    private FragmentInteractionSettingsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentInteractionSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews();
        initListeners();
    }

    private void initViews() {
        binding.screenProtection.setChecked(themePreferencesCache.isScreenProtectionEnabled());
        binding.extendedEditor.setChecked(themePreferencesCache.isExtendedEditorEnabled());
    }

    private void initListeners() {
        binding.screenProtection.setOnCheckedChangeListener((buttonView, isChecked) ->
                themePreferencesCache.setScreenProtection(isChecked));

        binding.extendedEditor.setOnCheckedChangeListener((buttonView, isChecked) ->
                themePreferencesCache.setExtendedEditor(isChecked));
        binding.feedbackNewEditor.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SEND_FEEDBACK_EDITOR))));
    }

    private void applyThemeColors() {
        if (getContext() == null) return;
        // Apply theme colors to views
        int colorSurfaceContainer = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorSurfaceContainer, Color.GRAY);
        int colorOnSurface = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurface, Color.GRAY);
        int colorOnSurfaceVariant = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.GRAY);
        int colorPrimary = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorPrimaryFixed, Color.GRAY);


        // Screen Protection Card and Switch
        binding.screenProtectionCard.setCardBackgroundColor(colorSurfaceContainer);
        binding.screenProtection.setTextColor(colorOnSurface);
        binding.screenProtectionDescription.setTextColor(colorOnSurfaceVariant);

        // Extended Editor Card and Switch
        binding.extendedEditorCard.setCardBackgroundColor(colorSurfaceContainer);
        binding.extendedEditor.setTextColor(colorOnSurface);
        binding.extendedEditorDescription.setTextColor(colorOnSurfaceVariant);

        updateSwitchColors(binding.screenProtection, colorPrimary, colorOnSurfaceVariant);
        updateSwitchColors(binding.extendedEditor, colorPrimary, colorOnSurfaceVariant);
    }


    public void updateThemeColors() {
        applyThemeColors();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;

    }
}