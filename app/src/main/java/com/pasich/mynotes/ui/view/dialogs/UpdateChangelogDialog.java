package com.pasich.mynotes.ui.view.dialogs;

import static com.pasich.mynotes.ui.view.activity.SupportActivity.PURCHASES_OPEN_EXTRA;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.pasich.mynotes.base.dialog.BaseDialogBottomSheets;
import com.pasich.mynotes.databinding.DialogUpdateChangelogBinding;
import com.pasich.mynotes.ui.view.activity.SupportActivity;
import com.pasich.mynotes.utils.changelog.ChangelogManager;
import com.pasich.mynotes.utils.navigation.GoogleTranslateHelper;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.noties.markwon.Markwon;

@AndroidEntryPoint
public class UpdateChangelogDialog extends BaseDialogBottomSheets {

    @Inject
    ChangelogManager changelogManager;

    @Inject
    Markwon markwon;

    private DialogUpdateChangelogBinding binding;

    public UpdateChangelogDialog() {
    }

    public static UpdateChangelogDialog newInstance() {
        return new UpdateChangelogDialog();
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        setState((BottomSheetDialog) requireDialog());

        binding = DialogUpdateChangelogBinding.inflate(inflater, container, false);

        initChangelogContent();
        initListeners();

        return binding.getRoot();
    }

    private void initChangelogContent() {
        String changelog = changelogManager.getChangelogForCurrentVersion();

        if (changelog.isEmpty()) {
            changelog = changelogManager.readRawChangelog();
        }

        // Markdown
        try {
            markwon.setMarkdown(binding.changelogText, changelog);
        } catch (Exception e) {
            binding.changelogText.setText(changelog);
        }
    }

    @Override
    public void initListeners() {

        // Close button
        binding.okButton.setOnClickListener(v -> {
            changelogManager.markChangelogRead();
            dismiss();
        });

        // Support button
        binding.supportButton.setOnClickListener(v -> {
            changelogManager.markChangelogRead();
            Intent intent = new Intent(requireActivity(), SupportActivity.class);
            intent.putExtra(PURCHASES_OPEN_EXTRA, true);
            startActivity(intent);

            dismiss();
        });

        binding.translateIcon.setOnClickListener(v -> {
            String text = binding.changelogText.getText().toString();
            if (!text.isEmpty()) {
                GoogleTranslateHelper.startTranslation(requireActivity(), text);
            }
        });

    }

    @Override
    public void setState(BottomSheetDialog dialog) {
        super.setState(dialog);
    }
}
