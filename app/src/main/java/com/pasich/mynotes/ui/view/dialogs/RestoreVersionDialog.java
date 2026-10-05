package com.pasich.mynotes.ui.view.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pasich.mynotes.R;

/**
 * "Restore this version?" A dialog fragment rather than a plain dialog, so it stays open through a
 * rotation and its answer reaches the recreated screen.
 */
public class RestoreVersionDialog extends DialogFragment {

    public static final String TAG = "RestoreVersionDialog";
    private static final String ARG_VERSION_ID = "versionId";

    /** The screen that restores the version. */
    public interface Host {
        void onRestoreConfirmed(long versionId);
    }

    @NonNull
    public static RestoreVersionDialog newInstance(long versionId) {
        RestoreVersionDialog dialog = new RestoreVersionDialog();
        Bundle args = new Bundle();
        args.putLong(ARG_VERSION_ID, versionId);
        dialog.setArguments(args);
        return dialog;
    }

    public long versionId() {
        return requireArguments().getLong(ARG_VERSION_ID);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new MaterialAlertDialogBuilder(requireContext())
                .setIcon(R.drawable.ic_history)
                .setTitle(R.string.version_restore_title)
                .setMessage(R.string.version_restore_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.version_restore,
                        (dialog, which) -> {
                            if (requireActivity() instanceof Host host) {
                                host.onRestoreConfirmed(versionId());
                            }
                        })
                .create();
    }
}
