package com.pasich.mynotes.ui.view.dialogs;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.dialog.BaseDialogBottomSheets;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.DialogShareOptionsBinding;
import com.pasich.mynotes.utils.file.FileExportUtils;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog for advanced sharing options.
 *
 * <p>Only the notes' ids live in the arguments, so the dialog can be rebuilt after a rotation or
 * after the process was killed while the file picker was open; the notes are then read again from
 * the database. Files are built and written on a background thread.
 */
public class ShareOptionsDialog extends BaseDialogBottomSheets {

    private static final String TAG = "ShareOptionsDialog";

    private static final String ARG_NOTE_IDS = "noteIds";
    private static final String ARG_SINGLE = "single";
    private static final String ARG_DATA_EXPORT = "dataExport";
    private static final String ARG_SINGLE_TITLE = "singleTitle";

    /** SQLite caps the number of bound parameters; ids are looked up in chunks below it. */
    private static final int ID_CHUNK = 500;

    private DialogShareOptionsBinding binding;
    // Activity result launchers for file saving
    private ActivityResultLauncher<Intent> saveTxtLauncher;
    private ActivityResultLauncher<Intent> savePdfLauncher;
    private ActivityResultLauncher<Intent> saveHtmlLauncher;

    /** The notes as handed in; null after the dialog was recreated, until read again. */
    @Nullable private List<Note> notes;

    @Nullable private Disposable pendingLoad;

    @EntryPoint
    @InstallIn(SingletonComponent.class)
    public interface ShareEntryPoint {
        AppDatabase appDatabase();
    }

    public ShareOptionsDialog() {}

    /**
     * @deprecated use {@link #newInstance(List, boolean)}; kept for callers not yet moved over.
     */
    @Deprecated
    @SuppressLint("ValidFragment")
    public ShareOptionsDialog(List<Note> selectedNotes, boolean isDataExport) {
        setArguments(arguments(selectedNotes, false, isDataExport));
        this.notes = selectedNotes == null ? null : new ArrayList<>(selectedNotes);
    }

    public static ShareOptionsDialog newInstance(@NonNull Note note) {
        return create(Collections.singletonList(note), true, false);
    }

    public static ShareOptionsDialog newInstance(@NonNull List<Note> selectedNotes) {
        return create(selectedNotes, false, false);
    }

    public static ShareOptionsDialog newInstance(
            @NonNull List<Note> selectedNotes, boolean isDataExport) {
        return create(selectedNotes, false, isDataExport);
    }

    private static ShareOptionsDialog create(
            List<Note> selectedNotes, boolean single, boolean isDataExport) {
        ShareOptionsDialog dialog = new ShareOptionsDialog();
        dialog.setArguments(arguments(selectedNotes, single, isDataExport));
        dialog.notes = new ArrayList<>(selectedNotes);
        return dialog;
    }

    @VisibleForTesting
    static Bundle arguments(List<Note> selectedNotes, boolean single, boolean isDataExport) {
        List<Note> list = selectedNotes == null ? Collections.emptyList() : selectedNotes;
        int[] ids = new int[list.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = list.get(i).getId();
        Bundle args = new Bundle();
        args.putIntArray(ARG_NOTE_IDS, ids);
        args.putBoolean(ARG_SINGLE, single);
        args.putBoolean(ARG_DATA_EXPORT, isDataExport);
        if (single && !list.isEmpty()) args.putString(ARG_SINGLE_TITLE, list.get(0).getTitle());
        return args;
    }

    /** What is exported: a file title and its text. */
    @VisibleForTesting
    static final class Export {
        final String title;
        final String content;

        Export(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }

    private static String titleOrStars(@Nullable String title) {
        return (title == null || title.isEmpty()) ? "***" : title;
    }

    /** The file title, known from the arguments alone. */
    @VisibleForTesting
    static String exportTitle(@NonNull Bundle args) {
        if (args.getBoolean(ARG_SINGLE)) return titleOrStars(args.getString(ARG_SINGLE_TITLE));
        return args.getBoolean(ARG_DATA_EXPORT) ? "Data_Export" : "Multiple_Notes";
    }

    /** Builds the export of {@code notes}, or null when there is nothing to export. */
    @Nullable
    @VisibleForTesting
    static Export buildExport(@NonNull Bundle args, @Nullable List<Note> notes) {
        if (notes == null || notes.isEmpty()) return null;
        if (args.getBoolean(ARG_SINGLE)) {
            Note note = notes.get(0);
            return new Export(titleOrStars(note.getTitle()), note.getValue());
        }
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            combined.append(titleOrStars(note.getTitle())).append("\n\n");
            if (note.getValue() != null) combined.append(note.getValue());
            if (i < notes.size() - 1) combined.append("\n\n\n===================\n\n");
        }
        return new Export(exportTitle(args), combined.toString());
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        saveTxtLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> onFileChosen(result, Format.TXT));
        savePdfLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> onFileChosen(result, Format.PDF));
        saveHtmlLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> onFileChosen(result, Format.HTML));
    }

    private enum Format {
        TXT,
        PDF,
        HTML
    }

    private Bundle args() {
        Bundle args = getArguments();
        return args == null ? new Bundle() : args;
    }

    private void onFileChosen(ActivityResult result, Format format) {
        Uri uri =
                result.getResultCode() == Activity.RESULT_OK && result.getData() != null
                        ? result.getData().getData()
                        : null;
        if (uri != null) {
            // Captured now: the dialog is gone before the file is written.
            Context app = requireContext().getApplicationContext();
            Bundle args = new Bundle(args());
            List<Note> known = notes;
            write(app, uri, format, args, known);
        }
        dismissAllowingStateLoss();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void write(
            Context app, Uri uri, Format format, Bundle args, @Nullable List<Note> known) {
        Single.fromCallable(
                        () -> {
                            List<Note> source = known != null ? known : loadNotes(app, args);
                            Export export = buildExport(args, source);
                            // Nothing left to export (the notes were deleted meanwhile): say so
                            // rather than
                            // writing an empty file that looks like a success.
                            if (export == null) return false;
                            switch (format) {
                                case PDF:
                                    return FileExportUtils.savePdfToUri(
                                            app, uri, export.title, export.content);
                                case HTML:
                                    return FileExportUtils.saveHtmlToUri(
                                            app,
                                            uri,
                                            export.title,
                                            export.content,
                                            args.getBoolean(ARG_SINGLE) ? null : source);
                                default:
                                    return FileExportUtils.saveTxtToUri(
                                            app, uri, export.title, export.content);
                            }
                        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        ok ->
                                Toast.makeText(
                                                app,
                                                ok
                                                        ? R.string.fileSavedSuccessfully
                                                        : R.string.errorSavingFile,
                                                Toast.LENGTH_SHORT)
                                        .show(),
                        error -> {
                            Log.e(TAG, "export failed", error);
                            Toast.makeText(app, R.string.errorSavingFile, Toast.LENGTH_SHORT)
                                    .show();
                        });
    }

    /** Reads the notes named in {@code args} back, in their order, skipping deleted ones. */
    @WorkerThread
    private static List<Note> loadNotes(Context app, Bundle args) {
        int[] ids = args.getIntArray(ARG_NOTE_IDS);
        List<Note> result = new ArrayList<>();
        if (ids == null || ids.length == 0) return result;
        AppDatabase db =
                EntryPointAccessors.fromApplication(app, ShareEntryPoint.class).appDatabase();
        Map<Integer, Note> byId = new HashMap<>();
        for (int start = 0; start < ids.length; start += ID_CHUNK) {
            List<Integer> chunk = new ArrayList<>();
            for (int i = start; i < Math.min(ids.length, start + ID_CHUNK); i++) chunk.add(ids[i]);
            for (Note note : db.noteDao().getNotesByIdsSync(chunk)) byId.put(note.getId(), note);
        }
        for (int id : ids) {
            Note note = byId.get(id);
            if (note != null) result.add(note);
        }
        return result;
    }

    /** Runs {@code action} with the notes, reading them again first if the dialog was recreated. */
    private void withExport(ExportAction action) {
        if (notes != null) {
            Export export = buildExport(args(), notes);
            if (export != null) action.run(export);
            else showError();
            return;
        }
        if (pendingLoad != null && !pendingLoad.isDisposed()) return;
        Context app = requireContext().getApplicationContext();
        Bundle args = new Bundle(args());
        pendingLoad =
                Single.fromCallable(() -> loadNotes(app, args))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                loaded -> {
                                    if (!isAdded()) return;
                                    notes = loaded;
                                    withExport(action);
                                },
                                error -> {
                                    Log.e(TAG, "loading notes failed", error);
                                    if (isAdded()) showError();
                                });
    }

    private interface ExportAction {
        void run(Export export);
    }

    private void showError() {
        Toast.makeText(requireContext(), R.string.errorSavingFile, Toast.LENGTH_SHORT).show();
        dismissAllowingStateLoss();
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
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
        binding.saveToGoogleDrive.setOnClickListener(v ->
                withExport(export -> {
                    String formattedContent =
                            FileExportUtils.formatNoteContent(export.title, export.content);
                    FileExportUtils.saveToGoogleDrive(
                            requireContext(), export.title, formattedContent);
                    dismiss();
                }));

        binding.saveAsTxt.setOnClickListener(v ->
                saveTxtLauncher.launch(FileExportUtils.createSaveTxtIntent(exportTitle(args()))));

        binding.saveAsPdf.setOnClickListener(v ->
                savePdfLauncher.launch(FileExportUtils.createSavePdfIntent(exportTitle(args()))));

        binding.saveAsHtml.setOnClickListener(v ->
                saveHtmlLauncher.launch(FileExportUtils.createSaveHtmlIntent(exportTitle(args()))));

        binding.shareViaOtherApps.setOnClickListener(v ->
                withExport(export -> {
                    String formattedContent =
                            FileExportUtils.formatNoteContent(export.title, export.content);
                    FileExportUtils.shareViaOtherApps(
                            requireActivity(), export.title, formattedContent);
                    dismiss();
                }));
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        clearListeners();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pendingLoad != null) pendingLoad.dispose();
    }

    private void clearListeners() {
        if (binding == null) return;
        binding.saveToGoogleDrive.setOnClickListener(null);
        binding.saveAsTxt.setOnClickListener(null);
        binding.saveAsPdf.setOnClickListener(null);
        binding.saveAsHtml.setOnClickListener(null);
        binding.shareViaOtherApps.setOnClickListener(null);
    }
}
