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

/**
 * Asks where a backup is saved to or restored from.
 *
 * <p>The fragment is recreated by the framework after a rotation or process death, through the
 * no-argument constructor, so it keeps nothing but its arguments: the choice is handed to the
 * hosting activity, which implements {@link Host}.
 */
public class BackupOptionsDialog extends BaseDialogBottomSheets {

    private static final String ARG_IS_RESTORE = "isRestore";

    /** The screen that acts on the choice; whichever instance of it is current. */
    public interface Host {
        /** The callback for a restore ({@code isRestore}) or for saving a backup. */
        @NonNull
        BackupOptionsCallback backupOptionsCallback(boolean isRestore);
    }

    private DialogBackupOptionsBinding binding;

    public BackupOptionsDialog() {}

    public static BackupOptionsDialog newInstance(boolean isRestore) {
        BackupOptionsDialog dialog = new BackupOptionsDialog();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_RESTORE, isRestore);
        dialog.setArguments(args);
        return dialog;
    }

    private boolean isRestore() {
        Bundle args = getArguments();
        return args != null && args.getBoolean(ARG_IS_RESTORE, false);
    }

    @Nullable
    private BackupOptionsCallback callback() {
        return getActivity() instanceof Host host ? host.backupOptionsCallback(isRestore()) : null;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        vibrateOpenDialog(true);
        setState((BottomSheetDialog) requireDialog());
        binding = DialogBackupOptionsBinding.inflate(inflater, container, false);

        initListeners();

        if (isRestore()) {
            binding.backupOptions.setText(R.string.restore_backup);
        } else {
            binding.backupOptions.setText(R.string.save_backup);
        }

        binding.googleDrive.setVisibility(
                FileExportUtils.isAppInstalled(getContext()) ? VISIBLE : GONE);

        return binding.getRoot();
    }

    @Override
    public void initListeners() {
        binding.googleDrive.setOnClickListener(v -> {
            BackupOptionsCallback callback = callback();
            if (callback != null) callback.onGoogleDrive();
            dismiss();
        });

        binding.deviceStorage.setOnClickListener(v -> {
            BackupOptionsCallback callback = callback();
            if (callback != null) callback.onDeviceStorage();
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
