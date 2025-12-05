package com.pasich.mynotes.ui.view.activity.noteEditor;

import static android.view.View.VISIBLE;
import static com.pasich.mynotes.extendedEditor.utils.EditorJsonUtils.findAttachmentByBlockId;
import static com.pasich.mynotes.extendedEditor.utils.EditorJsonUtils.findBlockIdByAttachment;
import static com.pasich.mynotes.utils.FormattedDataUtil.lastDayEditNote;

import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.AppPreferencesCache;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ActivityNoteExtendedEditorBinding;
import com.pasich.mynotes.extendedEditor.NoteEditorView;
import com.pasich.mynotes.extendedEditor.attach.AttachmentCleaner;
import com.pasich.mynotes.extendedEditor.models.EditorAttachment;
import com.pasich.mynotes.extendedEditor.models.SettingsEditorJsBridge;
import com.pasich.mynotes.extendedEditor.utils.EditorJSInterface;
import com.pasich.mynotes.extendedEditor.view.AttachmentActionsDialog;
import com.pasich.mynotes.ui.presenter.NotePresenter;
import com.pasich.mynotes.ui.view.activity.PhotoViewActivity;
import com.pasich.mynotes.utils.navigation.NoteExtras;

import dagger.hilt.android.AndroidEntryPoint;
import jakarta.inject.Inject;

@AndroidEntryPoint
public class NoteExtendedEditorActivity extends BaseNoteEditorActivity<ActivityNoteExtendedEditorBinding> implements NoteEditorView.OnFileChooserListener {
    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (binding != null) {
                    binding.noteEditor.onFileChooserResult(
                            result.getResultCode(),
                            result.getData()
                    );
                }
            }
    );
    @Inject
    AppPreferencesCache appPreferencesCache;
    private boolean isReadMode = false;

    @Override
    protected int getMenuResId() {
        return R.menu.menu_activity_toolbar_note_extendes;
    }

    @Override
    protected Toolbar getToolbar() {
        return binding.toolbar;
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

        EditorJSInterface bridge = new EditorJSInterface(
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
                    public void onTitleChanged(String title) {
                        runOnUiThread(() -> processTitleChange(title));

                    }

                    @Override
                    public void openPhoto(String blockId) {
                        handleImageOpen(blockId);
                    }

                    @Override
                    public void openFile(EditorAttachment att) {
                        runOnUiThread(() -> AttachmentActionsDialog.show(
                                NoteExtendedEditorActivity.this,
                                att,
                                (at, ls) -> handleAttachmentDelete(at, ls)
                        ));


                    }

                    @Override
                    public int getNoteId() {
                        return notePresenter.getNote().getId();
                    }

                    @Override
                    public void onError(String error) {
                        Log.e("ExtendedEditor", "NoteEditorView error:" + error);
                    }
                },
                binding.noteEditor.getWebView(),
                this,
                new SettingsEditorJsBridge(appPreferencesCache.getImageOpt())
        );

        binding.noteEditor.setEditorInterface(bridge);
    }

    @Override
    protected void onNewNoteInit(Note note) {
        binding.noteEditor.load(note);
    }

    @Override
    protected void setNewNoteTitle() {
        binding.titleToolbarDataCollapsed.setText(getString(R.string.new_note));
    }

    /**
     * Configures indents taking into account the keyboard for NoteActivity
     */
    @Override
    protected void applyEdgeToEdgeInsets(View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            // Get indents for system bars
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Set padding at the top for system bars only for the root view
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), 0);

            return insets;
        });
    }

    @Override
    public void initListeners() {
        binding.noteEditor.setOnFileChooserListener(this);
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

            if (image == null || image.url == null || image.url.isEmpty() || blockId == null || blockId.trim().isEmpty()) {
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
     * Handles attachment deletion flow:
     * 1. Locates the block ID inside Editor.js JSON.
     * 2. Passes the deletion request to the editor.
     * 3. Sends either the real file URL or null (if file is already missing).
     *
     * @param attach The attachment metadata.
     * @param lost   True if file does not exist on disk.
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


    /**
     * Process title changes with enhanced features
     */
    private void processTitleChange(String title) {
        notePresenter.extendedNoteChange(title, null);
    }

    /**
     * Process text changes with enhanced features
     */
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
        }

    }

    @Override
    public void loadingNote(Note note) {
        if (note == null) {
            finish();
            return;
        }

        changeTag(note.getTag() != null ? note.getTag() : "", false);
        binding.titleToolbarDataCollapsed.setText(getString(R.string.lastDateEditNote, lastDayEditNote(note.getDate())));
        binding.noteEditor.load(note);
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
            notePresenter.setAssignedTagNote(nameTag);
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
        new Thread(() -> AttachmentCleaner.cleanup(this, note)).start();
    }

    @Override
    public void reloadExtendedEditor() {
        binding.noteEditor.softRefresh();
    }

    @Override
    public void onNoteCopied(long newNoteId) {
        binding.duplicateTag.setText(
                getString(R.string.tagHastag, getString(R.string.duplicateTag))
        );
        binding.duplicateTag.setVisibility(VISIBLE);
    }
}