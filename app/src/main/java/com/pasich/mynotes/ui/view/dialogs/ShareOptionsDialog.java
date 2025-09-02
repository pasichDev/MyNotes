package com.pasich.mynotes.ui.view.dialogs;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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

    private static final String TAG = "ShareOptionsDialog";
    
    private final Note mNote;
    private final List<Note> mSelectedNotes;
    private DialogShareOptionsBinding binding;
    
    // Activity result launchers for file saving
    private ActivityResultLauncher<Intent> saveTxtLauncher;
    private ActivityResultLauncher<Intent> savePdfLauncher;
    private ActivityResultLauncher<Intent> saveHtmlLauncher;
    
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

                    if (uri != null && currentNoteTitle != null && currentNoteContent != null) {
                        FileExportUtils.saveTxtToUri(requireContext(), uri, currentNoteTitle, currentNoteContent);
                    } else {
                        Log.e(TAG, "Missing data for TXT save - URI: " + uri + ", Title: " + currentNoteTitle + ", Content: " + (currentNoteContent != null ? "available" : "null"));
                    }
                }
                dismiss(); // Dismiss after handling result
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
                dismiss(); // Dismiss after handling result
            }
        );
        
        saveHtmlLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        FileExportUtils.saveHtmlToUri(requireContext(), uri, currentNoteTitle, currentNoteContent, mSelectedNotes);
                    }
                }
                dismiss(); // Dismiss after handling result
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
            prepareNoteData();
            String formattedContent = FileExportUtils.formatNoteContent(currentNoteTitle, currentNoteContent);
            FileExportUtils.saveToGoogleDrive(requireContext(), currentNoteTitle, formattedContent);
            dismiss();
        });

        binding.saveAsTxt.setOnClickListener(v -> {
            prepareNoteData();
            Intent intent = FileExportUtils.createSaveTxtIntent(currentNoteTitle);
            saveTxtLauncher.launch(intent);
        });

        binding.saveAsPdf.setOnClickListener(v -> {
             prepareNoteData();
            Intent intent = FileExportUtils.createSavePdfIntent(currentNoteTitle);
            savePdfLauncher.launch(intent);
        });

        binding.saveAsHtml.setOnClickListener(v -> {
            prepareNoteData();
            Intent intent = FileExportUtils.createSaveHtmlIntent(currentNoteTitle);
            saveHtmlLauncher.launch(intent);
        });

        binding.shareViaOtherApps.setOnClickListener(v -> {
            prepareNoteData();
            String formattedContent = FileExportUtils.formatNoteContent(currentNoteTitle, currentNoteContent);
            FileExportUtils.shareViaOtherApps(requireActivity(), currentNoteTitle, formattedContent);
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
        binding.saveAsHtml.setOnClickListener(null);
        binding.shareViaOtherApps.setOnClickListener(null);
    }

    private void prepareNoteData() {
          if (mNote != null) {
             currentNoteTitle = (mNote.getTitle() == null || mNote.getTitle().isEmpty()) ? "***" : mNote.getTitle();
            currentNoteContent = mNote.getValue();

        } else if (mSelectedNotes != null && !mSelectedNotes.isEmpty()) {

            currentNoteTitle = "Multiple_Notes";
            StringBuilder combinedContent = new StringBuilder();

            for (int i = 0; i < mSelectedNotes.size(); i++) {
                Note note = mSelectedNotes.get(i);
                String noteTitle = (note.getTitle() == null || note.getTitle().isEmpty()) ? "***" : note.getTitle();


                combinedContent.append(noteTitle).append("\n\n");
                if (note.getValue() != null) {
                    combinedContent.append(note.getValue());
                }
                if (i < mSelectedNotes.size() - 1) {
                    combinedContent.append("\n\n\n===================\n\n");
                }
            }
            currentNoteContent = combinedContent.toString();
            
             } else {
            currentNoteTitle = "***";
            currentNoteContent = "";
        }
    }

}
