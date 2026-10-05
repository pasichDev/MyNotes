package com.pasich.mynotes.ui.view.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ConcatAdapter;
import com.google.android.material.snackbar.Snackbar;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.database.entities.NoteVersionEntity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ActivityNoteHistoryBinding;
import com.pasich.mynotes.ui.history.NoteVersionAdapter;
import com.pasich.mynotes.ui.view.dialogs.NoteVersionSheet;
import com.pasich.mynotes.ui.view.dialogs.RestoreVersionDialog;
import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;

/**
 * A note's local version history (pasichDev/MyNotes#174): every kept version, newest first, each
 * opening a preview against the current note from which it can be restored.
 *
 * <p>Restoring finishes with {@link #RESULT_OK}, so the screen that opened it — the editor, which
 * then reloads the note, or the notes list — can confirm it.
 */
@AndroidEntryPoint
public class NoteHistoryActivity extends BaseActivity
        implements NoteVersionSheet.Host, RestoreVersionDialog.Host {

    private static final String TAG = "NoteHistoryActivity";
    private static final String EXTRA_NOTE_ID = "noteId";

    @Inject DataManager dataManager;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private ActivityNoteHistoryBinding binding;
    private NoteVersionAdapter adapter;
    private int noteId;
    @Nullable private Note current;
    @NonNull private List<NoteVersionEntity> versions = new ArrayList<>();
    private boolean restoring;
    private boolean noteLoaded;
    private boolean versionsLoaded;

    @NonNull
    public static Intent intent(@NonNull Context context, int noteId) {
        return new Intent(context, NoteHistoryActivity.class).putExtra(EXTRA_NOTE_ID, noteId);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectTheme();
        binding = ActivityNoteHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupEdgeToEdgeInsets(binding.getRoot());

        noteId = getIntent().getIntExtra(EXTRA_NOTE_ID, 0);
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        adapter = new NoteVersionAdapter(this::openVersion);
        binding.versionList.setAdapter(new ConcatAdapter(new NoteVersionAdapter.Header(), adapter));
        initListeners();
        loadNote();
        observeVersions();
    }

    @Override
    public void initListeners() {}

    private void loadNote() {
        disposables.add(
                dataManager
                        .getNoteForId(noteId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                note -> {
                                    if (note.getId() == 0) {
                                        // Deleted meanwhile, e.g. by a sync.
                                        finish();
                                        return;
                                    }
                                    current = note;
                                    noteLoaded = true;
                                    String title = note.getTitle().trim();
                                    binding.toolbar.setSubtitle(title.isEmpty() ? null : title);
                                    notifyOpenSheet();
                                },
                                error -> Log.e(TAG, "loading the note failed", error)));
    }

    private void observeVersions() {
        disposables.add(
                dataManager
                        .getNoteVersions(noteId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                list -> {
                                    versions = list;
                                    versionsLoaded = true;
                                    adapter.submitList(list);
                                    notifyOpenSheet();
                                    boolean empty = list.isEmpty();
                                    binding.versionList.setVisibility(
                                            empty ? View.GONE : View.VISIBLE);
                                    binding.emptyState.setVisibility(
                                            empty ? View.VISIBLE : View.GONE);
                                },
                                error -> Log.e(TAG, "loading the versions failed", error)));
    }

    private void openVersion(@NonNull NoteVersionEntity version) {
        if (current == null || restoring) return;
        if (getSupportFragmentManager().findFragmentByTag(NoteVersionSheet.TAG) != null) return;
        NoteVersionSheet.newInstance(version.id)
                .show(getSupportFragmentManager(), NoteVersionSheet.TAG);
    }

    /** A preview kept open through a recreation shows its version once the data is back. */
    private void notifyOpenSheet() {
        if (getSupportFragmentManager().findFragmentByTag(NoteVersionSheet.TAG)
                instanceof NoteVersionSheet sheet) {
            sheet.onHostDataChanged();
        }
    }

    @Override
    public boolean isHistoryLoaded() {
        return noteLoaded && versionsLoaded;
    }

    @Nullable
    @Override
    public Note currentNote() {
        return current;
    }

    @Nullable
    @Override
    public NoteVersionEntity findVersion(long versionId) {
        for (NoteVersionEntity version : versions) {
            if (version.id == versionId) return version;
        }
        return null;
    }

    @Override
    public void onRestoreRequested(long versionId) {
        if (getSupportFragmentManager().findFragmentByTag(RestoreVersionDialog.TAG) != null) return;
        RestoreVersionDialog.newInstance(versionId)
                .show(getSupportFragmentManager(), RestoreVersionDialog.TAG);
    }

    @Override
    public void onRestoreConfirmed(long versionId) {
        restore(versionId);
    }

    private void restore(long versionId) {
        if (restoring) return;
        restoring = true;
        disposables.add(
                dataManager
                        .restoreNoteVersion(noteId, versionId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                restored -> {
                                    restoring = false;
                                    if (restored) {
                                        setResult(RESULT_OK);
                                        finish();
                                    } else {
                                        showRestoreFailed();
                                    }
                                },
                                error -> {
                                    restoring = false;
                                    Log.e(TAG, "restoring a version failed", error);
                                    showRestoreFailed();
                                }));
    }

    private void showRestoreFailed() {
        Snackbar.make(binding.getRoot(), R.string.version_restore_failed, Snackbar.LENGTH_LONG)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        disposables.dispose();
        super.onDestroy();
    }
}
