package com.pasich.mynotes.ui.view.fragment.settings;

import static com.pasich.mynotes.utils.constants.ContactLink.SEND_FEEDBACK_EDITOR;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.databinding.FragmentInteractionSettingsBinding;
import com.pasich.mynotes.ui.controllers.mainActivity.RedrawThemeController;

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
        binding.setExtendedDetailsVisible(false);
    }

    private void initListeners() {
        binding.screenProtection.setOnCheckedChangeListener((buttonView, isChecked) ->
                themePreferencesCache.setScreenProtection(isChecked));

        binding.extendedEditor.setOnCheckedChangeListener((buttonView, isChecked) ->
                themePreferencesCache.setExtendedEditor(isChecked));
        binding.feedbackNewEditor.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SEND_FEEDBACK_EDITOR))));
        binding.detailsExtended.setOnClickListener(v -> toggleDetails());
    }

    public void toggleDetails() {
        binding.setExtendedDetailsVisible(!binding.getExtendedDetailsVisible());
    }

    public void updateThemeColors() {
        if (getContext() == null) return;
        RedrawThemeController.styleCardBlock(
                binding.screenProtectionCard,
                null,
                binding.screenProtectionDescription,
                binding.screenProtection,
                requireContext()
        );
        RedrawThemeController.styleCardBlock(
                binding.extendedEditorCard,
                null,
                binding.extendedEditorDescription,
                binding.extendedEditor,
                requireContext()
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;

    }
}