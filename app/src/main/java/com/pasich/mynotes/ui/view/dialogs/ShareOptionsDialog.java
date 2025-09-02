package com.pasich.mynotes.ui.view.dialogs;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.pasich.mynotes.base.dialog.BaseDialogBottomSheets;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.DialogShareOptionsBinding;
import com.pasich.mynotes.utils.file.FileExportUtils;

import java.util.List;

/**
 * Dialog for advanced sharing options
 */
public class ShareOptionsDialog extends BaseDialogBottomSheets {

    private final Note mNote;
    private final List<Note> mSelectedNotes;
    private DialogShareOptionsBinding binding;
    
    // Activity result launchers for file saving
    private ActivityResultLauncher<Intent> saveTxtLauncher;
    private ActivityResultLauncher<Intent> savePdfLauncher;
    
    // Current data for saving
    private String currentNoteTitle;
    private String currentNoteContent;

    public ShareOptionsDialog(Note note) {
        this.mNote = note;
        this.mSelectedNotes = null;
    }

    public ShareOptionsDialog(List<Note> selectedNotes) {
        this.mNote = null;
        this.mSelectedNotes = selectedNotes;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize activity result launchers
        saveTxtLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        FileExportUtils.saveTxtToUri(requireContext(), uri, currentNoteContent);
                    }
                }
            }
        );
        
        savePdfLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        FileExportUtils.savePdfToUri(requireContext(), uri, currentNoteTitle, currentNoteContent);
                    }
                }
            }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        vibrateOpenDialog(true);
        setState((BottomSheetDialog) requireDialog());
        binding = DialogShareOptionsBinding.inflate(getLayoutInflater(), container, false);
        
        initListeners();
        return binding.getRoot();
    }

    @Override
    public void setState(BottomSheetDialog dialog) {
        super.setState(dialog);
    }

    @Override
    public void initListeners() {
        binding.saveToGoogleDrive.setOnClickListener(v -> {
            if (mNote != null) {
                FileExportUtils.saveToGoogleDrive(requireContext(), mNote.getTitle(), mNote.getValue());
            } else if (mSelectedNotes != null && !mSelectedNotes.isEmpty()) {
                StringBuilder combinedContent = new StringBuilder();
                String combinedTitle = "Multiple_Notes";
                
                for (int i = 0; i < mSelectedNotes.size(); i++) {
                    Note note = mSelectedNotes.get(i);
                    combinedContent.append("=== ").append(note.getTitle()).append(" === ");
                    combinedContent.append(note.getValue());
                    if (i < mSelectedNotes.size() - 1) {
                        combinedContent.append(" ");
                    }
                }
                
                FileExportUtils.saveToGoogleDrive(requireContext(), combinedTitle, combinedContent.toString());
            }
            dismiss();
        });

        binding.saveAsTxt.setOnClickListener(v -> {
            if (mNote != null) {
                currentNoteTitle = mNote.getTitle();
                currentNoteContent = mNote.getValue();
            } else if (mSelectedNotes != null && !mSelectedNotes.isEmpty()) {
                StringBuilder combinedContent = new StringBuilder();
                currentNoteTitle = "Multiple_Notes";
                
                for (int i = 0; i < mSelectedNotes.size(); i++) {
                    Note note = mSelectedNotes.get(i);
                    combinedContent.append("=== ").append(note.getTitle()).append(" === ");
                    combinedContent.append(note.getValue());
                    if (i < mSelectedNotes.size() - 1) {
                        combinedContent.append(" ");
                    }
                }
                currentNoteContent = combinedContent.toString();
            }
            
            Intent intent = FileExportUtils.createSaveTxtIntent(currentNoteTitle);
            saveTxtLauncher.launch(intent);
            dismiss();
        });

        binding.saveAsPdf.setOnClickListener(v -> {
            if (mNote != null) {
                currentNoteTitle = mNote.getTitle();
                currentNoteContent = mNote.getValue();
            } else if (mSelectedNotes != null && !mSelectedNotes.isEmpty()) {
                StringBuilder combinedContent = new StringBuilder();
                currentNoteTitle = "Multiple_Notes";
                
                for (int i = 0; i < mSelectedNotes.size(); i++) {
                    Note note = mSelectedNotes.get(i);
                    combinedContent.append("=== ").append(note.getTitle()).append(" === ");
                    combinedContent.append(note.getValue());
                    if (i < mSelectedNotes.size() - 1) {
                        combinedContent.append(" ");
                    }
                }
                currentNoteContent = combinedContent.toString();
            }
            
            Intent intent = FileExportUtils.createSavePdfIntent(currentNoteTitle);
            savePdfLauncher.launch(intent);
            dismiss();
        });

        binding.shareViaOtherApps.setOnClickListener(v -> {
            prepareNoteData();
            FileExportUtils.shareViaOtherApps(requireActivity(), currentNoteTitle, currentNoteContent);
            dismiss();
        });
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        clearListeners();
    }

    private void clearListeners() {
        binding.saveToGoogleDrive.setOnClickListener(null);
        binding.saveAsTxt.setOnClickListener(null);
        binding.saveAsPdf.setOnClickListener(null);
        binding.shareViaOtherApps.setOnClickListener(null);
    }

    private void prepareNoteData() {
        if (mNote != null) {
            currentNoteTitle = mNote.getTitle();
            currentNoteContent = mNote.getValue();
        } else if (mSelectedNotes != null && !mSelectedNotes.isEmpty()) {
            StringBuilder combinedContent = new StringBuilder();
            currentNoteTitle = "Multiple_Notes";
            
                for (int i = 0; i < mSelectedNotes.size(); i++) {
                    Note note = mSelectedNotes.get(i);
                    combinedContent.append("=== ").append(note.getTitle()).append(" ===\n");
                    combinedContent.append(note.getValue());
                    if (i < mSelectedNotes.size() - 1) {
                        combinedContent.append("\n\n");
                    }
                }            currentNoteContent = combinedContent.toString();
        }
    }

    @Override
    public void selectTheme() {
        // No specific theme handling needed
    }
}
