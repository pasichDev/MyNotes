package com.pasich.mynotes.ui.view.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.pasich.mynotes.data.model.backup.googleKeep.GoogleKeepImportResult;
import com.pasich.mynotes.databinding.DialogOtherAppImportBinding;

public class OtherAppImportDialog extends BottomSheetDialogFragment {
    private DialogOtherAppImportBinding binding;
    private ImportCallback callback;

    public interface ImportCallback {
        void onImportConfirmed(GoogleKeepImportResult result);
    }

    public static OtherAppImportDialog newInstance() {
        return new OtherAppImportDialog();
    }

    public void setCallback(ImportCallback callback) {
        this.callback = callback;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DialogOtherAppImportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setCancelable(false);
    }

    public void updateProgress(boolean scanning) {
        if (binding != null) {
            binding.progressBar.setVisibility(scanning ? View.VISIBLE : View.GONE);
            binding.resultContainer.setVisibility(scanning ? View.GONE : View.VISIBLE);
        }
    }

    public void showResult(GoogleKeepImportResult result) {
        if (binding != null) {
            updateProgress(false);

            if (result.hasError()) {
                binding.errorText.setText(result.getError());
                binding.errorText.setVisibility(View.VISIBLE);
                binding.resultContainer.setVisibility(View.GONE);
                binding.importButton.setVisibility(View.GONE);
            } else {
                binding.errorText.setVisibility(View.GONE);
                binding.resultContainer.setVisibility(View.VISIBLE);
                binding.importButton.setVisibility(View.VISIBLE);
                
                binding.notesCount.setText(String.valueOf(result.getTotalNotesCount()));
                binding.trashedNotesCount.setText(String.valueOf(result.getTrashedNotes().size()));
                binding.tagsCount.setText(String.valueOf(result.getLabels().size()));

                binding.importButton.setOnClickListener(v -> {
                    if (callback != null) {
                        callback.onImportConfirmed(result);
                    }
                    dismiss();
                });
            }

            binding.cancel.setOnClickListener(v -> dismiss());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}