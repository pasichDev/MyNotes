package com.pasich.mynotes.ui.view.dialogs;

import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.pasich.mynotes.databinding.DialogOtherAppImportBinding;
import com.pasich.mynotes.utils.backup.models.googleKeep.GoogleKeepImportResult;

/**
 * Shows the scan of another app's export and asks whether to import it.
 *
 * <p>The framework recreates this dialog after a rotation, but the scan that feeds it belongs to
 * the activity that started it and dies with it, and the scanned result cannot be put in a bundle.
 * So the dialog keeps the file it is about in its arguments and the hosting activity, through
 * {@link Host}, scans it again; meanwhile the dialog can always be cancelled.
 */
public class OtherAppImportDialog extends BottomSheetDialogFragment {

    public static final String TAG = "import_progress";
    private static final String ARG_FILE = "file";

    /** The screen that runs the scan and the import. */
    public interface Host {
        /** The user confirmed importing the scanned data. */
        void onOtherAppImportConfirmed(@NonNull GoogleKeepImportResult result);

        /** The user closed the dialog before importing; any scan still running is not needed. */
        void onOtherAppImportCancelled();
    }

    private DialogOtherAppImportBinding binding;

    /** The scan's outcome, kept until there is a view to show it in. */
    @Nullable private GoogleKeepImportResult result;

    public OtherAppImportDialog() {}

    public static OtherAppImportDialog newInstance(@NonNull Uri file) {
        OtherAppImportDialog dialog = new OtherAppImportDialog();
        Bundle args = new Bundle();
        args.putParcelable(ARG_FILE, file);
        dialog.setArguments(args);
        return dialog;
    }

    /** The export being scanned, or {@code null} if the dialog was created without one. */
    @Nullable
    public Uri getFile() {
        Bundle args = getArguments();
        return args == null ? null : args.getParcelable(ARG_FILE);
    }

    /** Whether the scan's outcome has reached this instance. */
    public boolean hasResult() {
        return result != null;
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DialogOtherAppImportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Back and a tap outside cancel too; onCancel tells the host.
        setCancelable(true);
        binding.cancel.setOnClickListener(v -> {
            notifyCancelled();
            dismiss();
        });
        if (result != null) {
            bindResult(result);
        } else {
            updateProgress(true);
        }
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        notifyCancelled();
    }

    private void notifyCancelled() {
        if (getActivity() instanceof Host host) {
            host.onOtherAppImportCancelled();
        }
    }

    public void updateProgress(boolean scanning) {
        if (binding != null) {
            binding.progressBar.setVisibility(scanning ? View.VISIBLE : View.GONE);
            binding.resultContainer.setVisibility(scanning ? View.GONE : View.VISIBLE);
        }
    }

    /** Shows the scan's outcome now, or as soon as the view exists. */
    public void showResult(@NonNull GoogleKeepImportResult result) {
        this.result = result;
        if (binding != null) {
            bindResult(result);
        }
    }

    private void bindResult(@NonNull GoogleKeepImportResult result) {
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
                if (getActivity() instanceof Host host) {
                    host.onOtherAppImportConfirmed(result);
                }
                dismiss();
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
