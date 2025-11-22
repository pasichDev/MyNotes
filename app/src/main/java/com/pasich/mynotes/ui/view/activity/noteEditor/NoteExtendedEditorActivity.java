package com.pasich.mynotes.ui.view.activity.noteEditor;

import static android.view.View.VISIBLE;
import static com.pasich.mynotes.utils.FormattedDataUtil.lastDayEditNote;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ActivityNoteExtendedEditorBinding;
import com.pasich.mynotes.extendedEditor.NoteEditorView;
import com.pasich.mynotes.ui.presenter.NotePresenter;
import com.pasich.mynotes.ui.view.dialogs.AttachmentActionsDialog;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class NoteExtendedEditorActivity extends BaseNoteEditorActivity<ActivityNoteExtendedEditorBinding> implements NoteEditorView.OnFileChooserListener {

    private boolean isReadMode = false;

    private ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
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

    @Override
    protected int getMenuResId() {
        return R.menu.menu_activity_toolbar_note_extendes;
    }

    @Override
    protected Toolbar getToolbar() {
        return binding.toolbar;
    }

    @Override
    protected Intent getCopyNoteIntent(long idNote) {
        return new Intent(this, NoteExtendedEditorActivity.class)
                .putExtra("NewNote", false)
                .putExtra("idNote", idNote)
                .putExtra("shareText", "")
                .putExtra("tagNote", "")
                .putExtra("betaMode", true);
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
        binding.noteEditor.setOnTitleChangedListener(this::processTitleChange);
        binding.noteEditor.setOnContentChangedListener(this::processTextChange);
        binding.noteEditor.setOnAttachmentClickListener(att -> AttachmentActionsDialog.show(this, att));
        binding.noteEditor.setOnFileChooserListener(this);
    }

    /**
     * Process title changes with enhanced features
     */
    private void processTitleChange(String title) {
        notePresenter.extendedTitleChange(title);
    }

    /**
     * Process text changes with enhanced features
     */
    private void processTextChange(String jsonData) {
        notePresenter.extendedNoteChange(jsonData);
    }

    @Override
    public void editIdNoteCreated(long idNote) {
        binding.titleToolbarDataCollapsed.setText(getString(R.string.lastDateEditNote, lastDayEditNote(notePresenter.getNote().getDate())));
        notePresenter.getNote().setId(Math.toIntExact(idNote));
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
            notePresenter.setTagNote(nameTag);
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
    public void openCopyNote(long idNote) {
        super.openCopyNote(idNote);
        finish();
    }

    @Override
    public void onOpenFileChooser(Intent intent, int requestCode) {
        fileChooserLauncher.launch(intent);
    }
}