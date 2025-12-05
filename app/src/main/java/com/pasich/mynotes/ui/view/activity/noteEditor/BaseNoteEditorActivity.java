package com.pasich.mynotes.ui.view.activity.noteEditor;

import static com.pasich.mynotes.utils.transition.TransitionUtil.buildContainerTransform;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.ui.contract.NoteContract;
import com.pasich.mynotes.ui.presenter.NotePresenter;
import com.pasich.mynotes.ui.view.dialogs.MoreNoteDialog;
import com.pasich.mynotes.utils.enums.SaveState;
import com.pasich.mynotes.utils.transition.CopyNoteAnimationUtil;

import java.util.Objects;

import javax.inject.Inject;

public abstract class BaseNoteEditorActivity<T extends ViewBinding> extends BaseActivity implements NoteContract.view {

    @Inject
    protected NoteContract.presenter notePresenter;

    // Menu for the save status indicator
    protected MenuItem saveStatusMenuItem;
    protected T binding;
    protected long idNote;

    protected abstract @MenuRes int getMenuResId();
    // Returns toolbar menu layout

    protected abstract Toolbar getToolbar();
    // Returns toolbar instance


    protected abstract T inflateBinding(LayoutInflater inflater);
    // Inflate and return view binding

    protected abstract void bindingSetPresenter(T binding);
    // Bind presenter to layout (if required)

    protected void onAfterPresenterReady() {
        // Optional hook after presenter initialization
    }

    protected abstract void applyEdgeToEdgeInsets(View rootView);
    // Apply window insets (IME/system bars)

    protected abstract void onNewNoteInit(Note note);
    // Load a new/empty note into the editor


    protected abstract void setNewNoteTitle();
    // Set title for a new note


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectTheme();
        settingsStatusBar(getWindow());

        idNote = getIntent().getLongExtra("idNote", 0);

        binding = inflateBinding(getLayoutInflater());
        setupSharedTransition(binding.getRoot(), idNote);
        setContentView(binding.getRoot());

        applyEdgeToEdgeInsets(binding.getRoot());

        bindingSetPresenter(binding);

        notePresenter.attachView(this);
        notePresenter.getLoadIntentData(getIntent());
        notePresenter.viewIsReady();

        onAfterPresenterReady();

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                notePresenter.closeActivity();
            }
        });
    }

    @Override
    public void initTypeActivity() {
        long id = notePresenter.getIdKey();

        // Existing note — load it
        if (id > 0) {
            notePresenter.loadingData(id);
            return;
        }

        // New note case
        if (notePresenter.getNewNotesKey()) {
            // Optional tag
            String tag = notePresenter.getAssignedTagNote();
            if (tag != null && tag.length() >= 2) {
                changeTag(tag, false);
            }

            // Default title
            setNewNoteTitle();
            activatedActivity();

            // Here every Activity loads its own “empty note view”
            onNewNoteInit(notePresenter.getNote());
        }
    }


    private void setupSharedTransition(View layout, long id) {
        layout.setTransitionName(String.valueOf(id));
        setEnterSharedElementCallback(new MaterialContainerTransformSharedElementCallback());
        getWindow().setSharedElementEnterTransition(buildContainerTransform(layout));
        getWindow().setSharedElementReturnTransition(buildContainerTransform(layout));
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(getMenuResId(), menu);
        saveStatusMenuItem = menu.findItem(R.id.saveStatusBut);
        return true;
    }

    protected void settingsStatusBar(Window window) {
        // Edge-to-edge decor
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // Control the color of status bar icons depending on night mode
        final int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(window, window.getDecorView());
        insetsController.setAppearanceLightStatusBars(currentNightMode == Configuration.UI_MODE_NIGHT_NO);

        // Transparent status bar
        window.setStatusBarColor(Color.TRANSPARENT);
    }

    @Override
    public void settingsActionBar() {
        Toolbar toolbar = getToolbar();
        if (toolbar == null) return;

        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            notePresenter.closeActivity();

        }

        if (item.getItemId() == R.id.moreBut) {
            new MoreNoteDialog(notePresenter.getNote(), notePresenter.getNewNotesKey(), true, 0, true).show(getSupportFragmentManager(), "MoreNote");
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void updateSaveStatus(SaveState saveState) {
        if (saveStatusMenuItem == null) return;

        switch (saveState) {
            case IDLE:
                saveStatusMenuItem.setVisible(false);
                break;

            case PENDING:
                saveStatusMenuItem.setVisible(true);
                saveStatusMenuItem.setIcon(R.drawable.ic_save_pending);
                saveStatusMenuItem.setTitle(getString(R.string.saveStatusPending));
                break;

            case SAVING:
                saveStatusMenuItem.setVisible(true);
                saveStatusMenuItem.setIcon(R.drawable.ic_save_saving_animated);
                saveStatusMenuItem.setTitle(getString(R.string.saveStatusSaving));
                break;

            case SAVED:
                saveStatusMenuItem.setVisible(true);
                saveStatusMenuItem.setIcon(R.drawable.ic_save_success);
                saveStatusMenuItem.setTitle(getString(R.string.saveStatusSaved));
                break;

            case ERROR:
                saveStatusMenuItem.setVisible(true);
                saveStatusMenuItem.setIcon(R.drawable.ic_save_error);
                saveStatusMenuItem.setTitle(getString(R.string.saveStatusError));
                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (notePresenter != null) {
            ((NotePresenter) notePresenter).cleanupHandlers();
            notePresenter.detachView();
        }

    }

    @Override
    public void closeActivityNotSaved() {
        finish();
    }

    @Override
    public void openCopyNote(long idNote) {
        notePresenter.copyNoteRequest();
    }

    @Override
    public void changeTextStyle() {
        // Not implemented extended
    }

    @Override
    public void changeTextSizeOnline(int sizeText) {
        // Not implemented extended
    }

    @Override
    public void changeTextSizeOffline() {
        // Not implemented extended
    }

    @Override
    public void initParam() {
        // Not implemented extended
    }

    @Override
    public void runCopyAnimation() {
        CopyNoteAnimationUtil.runCopyAnimation(binding.getRoot());
    }

}
