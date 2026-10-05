package com.pasich.mynotes.ui.view.activity.noteEditor;

import static android.view.View.VISIBLE;
import static com.pasich.mynotes.extendedEditor.utils.EditorJsonUtils.findAttachmentByBlockId;
import static com.pasich.mynotes.extendedEditor.utils.EditorJsonUtils.findBlockIdByAttachment;
import static com.pasich.mynotes.utils.FormattedDataUtil.lastDayEditNote;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.chip.Chip;
import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.AppPreferencesCache;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ActivityNoteExtendedEditorBinding;
import com.pasich.mynotes.extendedEditor.NoteEditorView;
import com.pasich.mynotes.extendedEditor.attach.AttachmentCleaner;
import com.pasich.mynotes.extendedEditor.models.EditorAttachment;
import com.pasich.mynotes.extendedEditor.models.SettingsEditorJsBridge;
import com.pasich.mynotes.extendedEditor.utils.EditorJSInterface;
import com.pasich.mynotes.extendedEditor.utils.ExtendedViewStateJson;
import com.pasich.mynotes.extendedEditor.view.AttachmentActionsDialog;
import com.pasich.mynotes.extendedEditor.view.CopyTextDialog;
import com.pasich.mynotes.ui.presenter.NotePresenter;
import com.pasich.mynotes.ui.view.activity.PhotoViewActivity;
import com.pasich.mynotes.utils.editor.NoteViewState;
import com.pasich.mynotes.utils.editor.NoteViewStateStore;
import com.pasich.mynotes.utils.editor.PositionRestorer;
import com.pasich.mynotes.utils.navigation.NoteExtras;
import dagger.hilt.android.AndroidEntryPoint;
import jakarta.inject.Inject;

@AndroidEntryPoint
public class NoteExtendedEditorActivity
        extends BaseNoteEditorActivity<ActivityNoteExtendedEditorBinding>
        implements NoteEditorView.OnFileChooserListener {
    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (binding != null) {
                            binding.noteEditor.onFileChooserResult(
                                    result.getResultCode(), result.getData());
                        }
                    });
    private static final String STATE_ANCHOR_INDEX = "extended.anchorIndex";
    private static final String STATE_ANCHOR_OFFSET = "extended.anchorOffset";
    private static final String STATE_DRAFT_TITLE = "extended.draftTitle";
    private static final String STATE_DRAFT_JSON = "extended.draftJson";
    private static final String STATE_CHOOSER_KIND = "extended.chooserKind";
    private static final String STATE_CHOOSER_INDEX = "extended.chooserIndex";

    /**
     * Largest unsaved document kept in the saved state. The draft only matters while a save is in
     * flight; a bigger one would risk the saved-state size limit for a few milliseconds of
     * protection.
     */
    private static final int MAX_DRAFT_CHARS = 64 * 1024;

    @Inject AppPreferencesCache appPreferencesCache;
    @Inject NoteViewStateStore noteViewStateStore;
    private boolean isReadMode = false;

    // A fresh open (not a recreation) of an existing note goes back to where it was left.
    private boolean restoreSavedPosition = false;

    private int restoredAnchorIndex = -1;
    private int restoredAnchorOffset = 0;
    private String draftTitle;
    private String draftJson;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            restoredAnchorIndex = savedInstanceState.getInt(STATE_ANCHOR_INDEX, -1);
            restoredAnchorOffset = savedInstanceState.getInt(STATE_ANCHOR_OFFSET, 0);
            draftTitle = savedInstanceState.getString(STATE_DRAFT_TITLE);
            draftJson = savedInstanceState.getString(STATE_DRAFT_JSON);
        }
        super.onCreate(savedInstanceState);
        // The note loads asynchronously, so this is decided before it arrives; the store is
        // injected by super.onCreate.
        long openedId = getIntent().getLongExtra(NoteExtras.EXTRA_ID_NOTE, 0);
        restoreSavedPosition =
                savedInstanceState == null && openedId > 0 && hasSavedPosition(openedId);
        if (binding != null && restoreSavedPosition) {
            // The caret goes back where it was, so the first block must not take it first.
            binding.noteEditor.setAutofocus(false);
        }
        if (savedInstanceState != null && binding != null) {
            // A picker opened by the previous instance answers this one.
            binding.noteEditor.restoreChooserState(
                    savedInstanceState.getString(STATE_CHOOSER_KIND),
                    savedInstanceState.getInt(STATE_CHOOSER_INDEX, -1));
        }
    }

    private boolean hasSavedPosition(long noteId) {
        NoteViewState saved = noteViewStateStore.get(noteId);
        return saved != null && saved.extended != null;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding == null) return;
        outState.putInt(STATE_ANCHOR_INDEX, binding.noteEditor.getAnchorIndex());
        outState.putInt(STATE_ANCHOR_OFFSET, binding.noteEditor.getAnchorOffset());
        if (binding.noteEditor.getChooserKind() != null) {
            outState.putString(STATE_CHOOSER_KIND, binding.noteEditor.getChooserKind());
            outState.putInt(STATE_CHOOSER_INDEX, binding.noteEditor.getChooserBlockIndex());
        }
        if (notePresenter != null && notePresenter.hasUnsavedChanges()) {
            Note note = notePresenter.getNote();
            String json = note.getValueJson();
            if (json == null || json.length() <= MAX_DRAFT_CHARS) {
                outState.putString(STATE_DRAFT_TITLE, note.getTitle());
                outState.putString(STATE_DRAFT_JSON, json);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveViewState();
    }

    /** Keeps where the note is being read or edited, for the next time it is opened. */
    private void saveViewState() {
        if (binding == null || notePresenter == null) return;
        long noteId = notePresenter.getIdKey();
        NoteViewState.Extended state =
                ExtendedViewStateJson.fromPage(binding.noteEditor.getLastViewState());
        if (noteId > 0 && state != null) noteViewStateStore.putExtended(noteId, state);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (binding == null || notePresenter == null || !notePresenter.hasNote()) return;
        // Write what the presenter already holds, then collect whatever the editor is still
        // batching; that answer is written as soon as it arrives.
        notePresenter.flushPending();
        binding.noteEditor.requestFlush();
    }

    @Override
    protected int getMenuResId() {
        return R.menu.menu_activity_toolbar_note_extendes;
    }

    @Override
    protected Toolbar getToolbar() {
        return binding.toolbar;
    }

    @Override
    protected Chip getReminderChip() {
        return binding.reminderChip;
    }

    @Override
    protected ActivityNoteExtendedEditorBinding inflateBinding(LayoutInflater inflater) {
        return ActivityNoteExtendedEditorBinding.inflate(inflater);
    }

    @Override
    protected void bindingSetPresenter(ActivityNoteExtendedEditorBinding binding) {
        binding.setPresenter((NotePresenter) notePresenter);
    }

    @Override
    protected void onAfterPresenterReady() {
        notePresenter.setExtendedEditor(true);

        EditorJSInterface bridge =
                new EditorJSInterface(
                        new EditorJSInterface.EditorListener() {

                            @Override
                            public void onEditorReady() {
                                binding.noteEditor.onEditorReadyFromBridge();
                            }

                            @Override
                            public void onContentChanged(String json) {
                                runOnUiThread(() -> processTextChange(json));
                            }

                            @Override
                            public void onContentFlushed(String json) {
                                runOnUiThread(
                                        () -> {
                                            processTextChange(json);
                                            notePresenter.flushPending();
                                        });
                            }

                            @Override
                            public void onViewportAnchor(int blockIndex, int offsetPx) {
                                runOnUiThread(
                                        () -> {
                                            if (binding != null) {
                                                binding.noteEditor.onViewportAnchor(
                                                        blockIndex, offsetPx);
                                            }
                                        });
                            }

                            @Override
                            public void onViewState(String json) {
                                runOnUiThread(
                                        () -> {
                                            if (binding != null) {
                                                binding.noteEditor.onViewState(json);
                                            }
                                        });
                            }

                            @Override
                            public void onNoteRendered() {
                                if (binding != null) binding.noteEditor.onNoteRenderedFromBridge();
                            }

                            @Override
                            public void onTitleChanged(String title) {
                                runOnUiThread(() -> processTitleChange(title));
                            }

                            @Override
                            public void openPhoto(String blockId) {
                                runOnUiThread(() -> handleImageOpen(blockId));
                            }

                            @Override
                            public void openFile(EditorAttachment att) {
                                runOnUiThread(
                                        () ->
                                                AttachmentActionsDialog.show(
                                                        NoteExtendedEditorActivity.this,
                                                        att,
                                                        (at, ls) ->
                                                                handleAttachmentDelete(at, ls)));
                            }

                            @Override
                            public int getNoteId() {
                                // Called on the WebView's bridge thread: read the id, never the
                                // note the UI thread is editing.
                                return (int) notePresenter.getIdKey();
                            }

                            @Override
                            public void onError(String error) {
                                Log.e("ExtendedEditor", "NoteEditorView error:" + error);
                            }
                        },
                        binding.noteEditor.getWebView(),
                        this,
                        new SettingsEditorJsBridge(appPreferencesCache.getImageOpt()));

        binding.noteEditor.setEditorInterface(bridge);
    }

    @Override
    protected void onNewNoteInit(Note note) {
        binding.noteEditor.load(note);
        updateReminderChip(note);
    }

    @Override
    protected void setNewNoteTitle() {
        binding.titleToolbarDataCollapsed.setText(getString(R.string.new_note));
    }

    /**
     * The window draws edge to edge, so the keyboard does not resize it: the root is padded by the
     * keyboard or the navigation bar, whichever is taller. Without it the keyboard covered the
     * lower half of the editor and the caret typed out of sight; the editor page keeps the caret
     * visible when its height changes.
     */
    @Override
    protected void applyEdgeToEdgeInsets(View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(
                rootView,
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                    int bottom = Math.max(ime.bottom, systemBars.bottom);
                    v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), bottom);
                    return insets;
                });
    }

    @Override
    public void initListeners() {
        binding.noteEditor.setOnFileChooserListener(this);
        binding.noteEditor.setOnContextDialogListener(
                () ->
                        CopyTextDialog.show(
                                this,
                                null, // title
                                notePresenter.getNote().getValue() // plain text
                                ));
    }

    /**
     * Opens the full-size image viewer for a clicked Editor.js image block.
     *
     * @param blockId The unique ID of the Editor.js block.
     */
    private void handleImageOpen(String blockId) {
        try {

            // Try to find the matching EditorAttachment for this block
            EditorAttachment image = findAttachmentByBlockId(notePresenter.getNote(), blockId);

            if (image == null
                    || image.url == null
                    || image.url.isEmpty()
                    || blockId == null
                    || blockId.trim().isEmpty()) {
                Toast.makeText(this, getString(R.string.openImageError), Toast.LENGTH_SHORT).show();
                return;
            }

            // Open PhotoViewActivity
            Intent intent = new Intent(this, PhotoViewActivity.class);
            intent.putExtra(PhotoViewActivity.EXTRA_URI, image.url);

            startActivity(intent);

        } catch (Exception e) {
            // Any unexpected error
            Toast.makeText(this, getString(R.string.openImageError), Toast.LENGTH_SHORT).show();
            Log.e("PHOTO_OPEN", "handleImageOpen() failed", e);
        }
    }

    /**
     * Handles attachment deletion flow: 1. Locates the block ID inside Editor.js JSON. 2. Passes
     * the deletion request to the editor. 3. Sends either the real file URL or null (if file is
     * already missing).
     *
     * @param attach The attachment metadata.
     * @param lost True if file does not exist on disk.
     */
    private void handleAttachmentDelete(EditorAttachment attach, boolean lost) {

        String blockId = findBlockIdByAttachment(notePresenter.getNote(), attach);

        if (blockId == null) {
            Log.w("AttachmentDelete", "Block not found for attachment: " + attach.url);
            return;
        }

        // If the file is missing → pass null to JS (Android should not delete)
        String urlOrNull = lost ? null : attach.url;

        binding.noteEditor.deleteBlock(blockId, urlOrNull);
    }

    /** Process title changes with enhanced features */
    private void processTitleChange(String title) {
        notePresenter.extendedNoteChange(title, null);
    }

    /** Process text changes with enhanced features */
    private void processTextChange(String jsonData) {
        notePresenter.extendedNoteChange(null, jsonData);
    }

    @Override
    public void activatedActivity() {
        binding.setActivateEdit(true);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.actionRead) {
            isReadMode = !isReadMode;
            binding.noteEditor.actionRead();
            item.setIcon(isReadMode ? R.drawable.ic_edit : R.drawable.ic_read);
            item.setTitle(isReadMode ? R.string.read_mode_exit : R.string.read_mode_enter);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (binding != null) {
            binding.titleToolbarTagCollapsed.setOnClickListener(null);
            binding.noteEditor.release();
            if (binding.noteEditor.getParent() instanceof ViewGroup) {
                ((ViewGroup) binding.noteEditor.getParent()).removeView(binding.noteEditor);
            }
        }
    }

    @Override
    public void loadingNote(Note note) {
        if (note == null) {
            finish();
            return;
        }

        changeTag(note.getTag() != null ? note.getTag() : "", false);
        binding.titleToolbarDataCollapsed.setText(
                getString(R.string.lastDateEditNote, lastDayEditNote(note.getDate())));

        if (restoredAnchorIndex >= 0) {
            binding.noteEditor.setRestoreAnchor(restoredAnchorIndex, restoredAnchorOffset);
            restoredAnchorIndex = -1;
        } else if (restoreSavedPosition) {
            restoreSavedPosition = false;
            NoteViewState saved = noteViewStateStore.get(note.getId());
            PositionRestorer.ExtendedTarget target =
                    PositionRestorer.restoreExtended(
                            saved != null ? saved.extended : null,
                            ExtendedViewStateJson.blockIds(note.getValueJson()));
            if (target.match != PositionRestorer.Match.TOP) {
                binding.noteEditor.setRestoreViewState(
                        ExtendedViewStateJson.toPage(target).toString());
            }
        }

        String pendingTitle = draftTitle;
        String pendingJson = draftJson;
        draftTitle = null;
        draftJson = null;
        if (pendingTitle == null && pendingJson == null) {
            binding.noteEditor.load(note);
            return;
        }

        // Edits made before a recreation that had not reached the database: show them, and hand
        // them to the presenter once it holds the loaded note so they are compared with what is
        // stored and saved.
        Note shown = new Note();
        shown.copyFrom(note);
        shown.setId(note.getId());
        if (pendingTitle != null) shown.setTitle(pendingTitle);
        if (pendingJson != null) shown.setValueJson(pendingJson);
        binding.noteEditor.load(shown);
        binding.getRoot().post(() -> notePresenter.extendedNoteChange(pendingTitle, pendingJson));
    }

    @Override
    public void closeNoteActivity() {
        if (binding == null || notePresenter == null) {
            supportFinishAfterTransition();
            return;
        }
        binding.getRoot().clearFocus();
        supportFinishAfterTransition();
    }

    @Override
    public void changeTag(String nameTag, boolean change) {
        if (change) {
            notePresenter.getNote().setTag(nameTag);
        }
        if (!nameTag.isEmpty()) {
            String tagText = getString(R.string.tagHastag, nameTag);
            binding.titleToolbarTagCollapsed.setText(tagText);
            binding.titleToolbarTagCollapsed.setVisibility(VISIBLE);
        } else {
            binding.titleToolbarTagCollapsed.setVisibility(View.GONE);
        }
    }

    @Override
    public void onOpenFileChooser(Intent intent, int requestCode) {
        fileChooserLauncher.launch(intent);
    }

    @Override
    public void runAttachmentsCleanup(Note note) {
        new Thread(() -> AttachmentCleaner.cleanup(getApplicationContext(), note)).start();
    }

    @Override
    public void reloadExtendedEditor() {
        binding.noteEditor.softRefresh();
    }

    @Override
    public void onNoteCopied(long newNoteId) {
        binding.duplicateTag.setText(
                getString(R.string.tagHastag, getString(R.string.duplicateTag)));
        binding.duplicateTag.setVisibility(VISIBLE);
    }
}
