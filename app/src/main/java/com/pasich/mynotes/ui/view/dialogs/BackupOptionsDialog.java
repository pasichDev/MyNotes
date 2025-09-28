package com.pasich.mynotes.ui.view.dialogs;


import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.dialog.BaseDialogBottomSheets;
import com.pasich.mynotes.base.view.BackupOptionsCallback;
import com.pasich.mynotes.databinding.DialogBackupOptionsBinding;
import com.pasich.mynotes.utils.file.FileExportUtils;

public class BackupOptionsDialog extends BaseDialogBottomSheets {

    private DialogBackupOptionsBinding binding;
    private final BackupOptionsCallback callback;
    private final boolean isRestore;

    public BackupOptionsDialog(BackupOptionsCallback callback, boolean isRestore) {
        this.callback = callback;
        this.isRestore = isRestore;
    }

    public static BackupOptionsDialog newInstance(BackupOptionsCallback callback, boolean isRestore) {
        return new BackupOptionsDialog(callback, isRestore);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        vibrateOpenDialog(true);
        setState((BottomSheetDialog) requireDialog());
        binding = DialogBackupOptionsBinding.inflate(inflater, container, false);

        initListeners();

        if (isRestore) {
            binding.backupOptions.setText(R.string.restore_backup);
        } else {
            binding.backupOptions.setText(R.string.save_backup);
        }

        binding.googleDrive.setVisibility(FileExportUtils.isAppInstalled(getContext()) ? VISIBLE : GONE);

        return binding.getRoot();
    }

    @Override
    public void initListeners() {
        binding.googleDrive.setOnClickListener(v -> {
            if (callback != null) callback.onGoogleDrive();
            dismiss();
        });

        binding.deviceStorage.setOnClickListener(v -> {
            if (callback != null) callback.onDeviceStorage();
            dismiss();
        });


    }
}
