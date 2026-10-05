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
import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.NoteOpeningPreferences;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.databinding.FragmentInteractionSettingsBinding;
import com.pasich.mynotes.ui.controllers.mainActivity.RedrawThemeController;
import com.pasich.mynotes.ui.view.dialogs.settings.ChoiceDialog;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class InteractionSettingsFragment extends Fragment {

    @Inject ThemePreferencesCache themePreferencesCache;
    @Inject NoteOpeningPreferences noteOpeningPreferences;

    private static final ChoiceDialog.Option[] MODE_OPTIONS = {
        new ChoiceDialog.Option(
                R.drawable.ic_auto_mode,
                R.string.note_opening_mode_auto,
                R.string.note_opening_mode_auto_summary),
        new ChoiceDialog.Option(
                R.drawable.ic_read,
                R.string.note_opening_mode_read,
                R.string.note_opening_mode_read_summary),
        new ChoiceDialog.Option(
                R.drawable.ic_edit,
                R.string.note_opening_mode_edit,
                R.string.note_opening_mode_edit_summary)
    };

    /** In the order of {@link #MODE_OPTIONS}. */
    private static final NoteOpeningPreferences.OpenMode[] MODES = {
        NoteOpeningPreferences.OpenMode.AUTO,
        NoteOpeningPreferences.OpenMode.READ,
        NoteOpeningPreferences.OpenMode.EDIT
    };

    private static final ChoiceDialog.Option[] POSITION_OPTIONS = {
        new ChoiceDialog.Option(
                R.drawable.ic_history,
                R.string.note_opening_position_last,
                R.string.note_opening_position_last_summary),
        new ChoiceDialog.Option(
                R.drawable.ic_vertical_align_top,
                R.string.note_opening_position_start,
                R.string.note_opening_position_start_summary)
    };

    /** In the order of {@link #POSITION_OPTIONS}. */
    private static final NoteOpeningPreferences.OpenPosition[] POSITIONS = {
        NoteOpeningPreferences.OpenPosition.LAST, NoteOpeningPreferences.OpenPosition.START
    };

    private FragmentInteractionSettingsBinding binding;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
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
        binding.doubleTapToEdit.setChecked(noteOpeningPreferences.isDoubleTapToEdit());
        updateOpeningValues();
    }

    /** Shows the chosen opening mode and position under their titles. */
    private void updateOpeningValues() {
        int mode = indexOf(MODES, noteOpeningPreferences.getOpenMode());
        binding.openModeValue.setText(MODE_OPTIONS[mode].name());
        int position = indexOf(POSITIONS, noteOpeningPreferences.getOpenPosition());
        binding.openPositionValue.setText(POSITION_OPTIONS[position].name());
    }

    private static <T> int indexOf(T[] values, T value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) return i;
        }
        return 0;
    }

    private void chooseOpenMode() {
        ChoiceDialog.show(
                requireContext(),
                R.string.note_opening_mode,
                MODE_OPTIONS,
                indexOf(MODES, noteOpeningPreferences.getOpenMode()),
                which -> {
                    noteOpeningPreferences.setOpenMode(MODES[which]);
                    if (binding != null) updateOpeningValues();
                });
    }

    private void chooseOpenPosition() {
        ChoiceDialog.show(
                requireContext(),
                R.string.note_opening_position,
                POSITION_OPTIONS,
                indexOf(POSITIONS, noteOpeningPreferences.getOpenPosition()),
                which -> {
                    noteOpeningPreferences.setOpenPosition(POSITIONS[which]);
                    if (binding != null) updateOpeningValues();
                });
    }

    private void initListeners() {
        binding.screenProtection.setOnCheckedChangeListener(
                (buttonView, isChecked) -> themePreferencesCache.setScreenProtection(isChecked));

        binding.extendedEditor.setOnCheckedChangeListener(
                (buttonView, isChecked) -> themePreferencesCache.setExtendedEditor(isChecked));
        binding.feedbackNewEditor.setOnClickListener(
                v ->
                        startActivity(
                                new Intent(Intent.ACTION_VIEW, Uri.parse(SEND_FEEDBACK_EDITOR))));
        binding.detailsExtended.setOnClickListener(v -> toggleDetails());
        binding.openModeCard.setOnClickListener(v -> chooseOpenMode());
        binding.openPositionCard.setOnClickListener(v -> chooseOpenPosition());
        binding.doubleTapToEdit.setOnCheckedChangeListener(
                (buttonView, isChecked) -> noteOpeningPreferences.setDoubleTapToEdit(isChecked));
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
                requireContext());
        RedrawThemeController.styleCardBlock(
                binding.extendedEditorCard,
                null,
                binding.extendedEditorDescription,
                binding.extendedEditor,
                requireContext());
        RedrawThemeController.styleCardBlock(
                binding.openModeCard,
                binding.openModeTitle,
                binding.openModeValue,
                null,
                requireContext());
        RedrawThemeController.styleTextVariant(binding.openModeDescription, requireContext());
        RedrawThemeController.styleCardBlock(
                binding.openPositionCard,
                binding.openPositionTitle,
                binding.openPositionValue,
                null,
                requireContext());
        RedrawThemeController.styleCardBlock(
                binding.doubleTapCard,
                null,
                binding.doubleTapDescription,
                binding.doubleTapToEdit,
                requireContext());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
