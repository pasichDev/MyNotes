package com.pasich.mynotes.ui.view.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.pasich.mynotes.base.dialog.BaseDialogBottomSheets;
import com.pasich.mynotes.data.database.entities.NoteVersionEntity;
import com.pasich.mynotes.data.history.NoteHistory;
import com.pasich.mynotes.data.history.NoteVersionReason;
import com.pasich.mynotes.data.history.NoteVersionRestore;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.SheetNoteVersionBinding;
import com.pasich.mynotes.ui.history.NoteVersionText;
import com.pasich.mynotes.ui.sync.SyncConflictPresentation;

/**
 * Preview of one kept version next to the current note, with the part where they differ highlighted
 * in both, and the way to restore it.
 *
 * <p>The sheet holds no data of its own: the host activity has the versions and the current note,
 * so the sheet survives recreation and closes itself if its version has gone.
 */
public class NoteVersionSheet extends BaseDialogBottomSheets {

    public static final String TAG = "NoteVersionSheet";
    private static final String ARG_VERSION_ID = "versionId";

    /** Longest stretch of the version shown, windowed around the difference. */
    private static final int VERSION_LIMIT = 4000;

    /** Longest stretch of the current note shown; it is there for comparison only. */
    private static final int CURRENT_LIMIT = 320;

    /** The activity hosting the sheet. */
    public interface Host {
        @Nullable
        Note currentNote();

        @Nullable
        NoteVersionEntity findVersion(long versionId);

        void onRestoreRequested(long versionId);
    }

    @Nullable private Host host;
    private SheetNoteVersionBinding binding;
    private long versionId;

    @NonNull
    public static NoteVersionSheet newInstance(long versionId) {
        NoteVersionSheet sheet = new NoteVersionSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_VERSION_ID, versionId);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Host h) host = h;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        versionId = getArguments() != null ? getArguments().getLong(ARG_VERSION_ID) : 0L;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        setState((BottomSheetDialog) requireDialog());
        binding = SheetNoteVersionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Note current = host == null ? null : host.currentNote();
        NoteVersionEntity version = host == null ? null : host.findVersion(versionId);
        if (current == null || version == null) {
            dismissAllowingStateLoss();
            return;
        }
        bind(current, version);
        initListeners();
    }

    private void bind(@NonNull Note current, @NonNull NoteVersionEntity version) {
        Context context = requireContext();
        binding.versionSheetTitle.setText(NoteVersionText.when(context, version.createdAt));
        binding.versionSheetReason.setText(
                NoteVersionText.reasonLabel(NoteVersionReason.fromStored(version.reason)));

        String versionText = NoteVersionText.readable(version.title, version.value);
        String currentText = NoteVersionText.readable(current.getTitle(), current.getValue());
        binding.versionSheetText.setText(
                NoteVersionText.highlighted(
                        context,
                        SyncConflictPresentation.compare(versionText, currentText, VERSION_LIMIT)
                                .first));
        binding.versionSheetCurrent.setText(
                NoteVersionText.highlighted(
                        context,
                        SyncConflictPresentation.compare(versionText, currentText, CURRENT_LIMIT)
                                .second));

        Note restored = new Note();
        restored.copyFrom(current);
        NoteVersionRestore plan = NoteVersionRestore.plan(current, version);
        plan.applyTo(restored);
        boolean same = NoteHistory.sameText(current, restored);
        binding.versionSheetSame.setVisibility(same ? View.VISIBLE : View.GONE);
        binding.versionSheetRestore.setEnabled(!same);
        binding.versionSheetAttachments.setVisibility(
                plan.droppedAttachments > 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void initListeners() {
        binding.versionSheetClose.setOnClickListener(v -> dismiss());
        binding.versionSheetRestore.setOnClickListener(
                v -> {
                    Host target = host;
                    dismiss();
                    if (target != null) target.onRestoreRequested(versionId);
                });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        host = null;
        super.onDetach();
    }
}
