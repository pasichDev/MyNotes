package com.pasich.mynotes.ui.view.activity;

import static android.content.ContentValues.TAG;
import static com.pasich.mynotes.utils.FormattedDataUtil.lastDayEditNote;
import static com.pasich.mynotes.utils.transition.TransitionUtil.buildContainerTransform;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.base.simplifications.TextWatcher;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ActivityNoteBinding;
import com.pasich.mynotes.ui.contract.NoteContract;
import com.pasich.mynotes.ui.presenter.NotePresenter;
import com.pasich.mynotes.ui.view.dialogs.MoreNoteDialog;
import com.pasich.mynotes.ui.view.dialogs.note.LinkInfoDialog;
import com.pasich.mynotes.ui.view.dialogs.note.popupWindowsTagNote.PopupWindowsTagNote;
import com.pasich.mynotes.utils.CustomLinkMovementMethod;
import com.pasich.mynotes.utils.constants.NameTransition;
import com.pasich.mynotes.utils.constants.SnackBarInfo;

import java.util.Date;
import java.util.Objects;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class NoteActivity extends BaseActivity implements NoteContract.view {

    public ActivityNoteBinding binding;
    @Inject
    public NoteContract.presenter notePresenter;


    @Override
    public void onCreate(Bundle savedInstanceState) {

        selectTheme();
        settingsStatusBar(getWindow());
        long idNote = getIntent().getLongExtra("idNote", 0);
        binding = ActivityNoteBinding.inflate(getLayoutInflater());
        binding.noteLayout.setTransitionName(idNote == 0 ? NameTransition.fabTransaction : String.valueOf(idNote));
        setEnterSharedElementCallback(new MaterialContainerTransformSharedElementCallback());
        getWindow().setSharedElementEnterTransition(buildContainerTransform(binding.noteLayout));
        getWindow().setSharedElementReturnTransition(buildContainerTransform(binding.noteLayout));
        super.onCreate(savedInstanceState);
        setContentView(binding.getRoot());
        binding.setPresenter((NotePresenter) notePresenter);
        notePresenter.attachView(this);
        notePresenter.getLoadIntentData(getIntent());
        notePresenter.viewIsReady();


    }


    /**
     * Method that enables Motion Animation smooth transition support
     *
     * @param mWindow - activity window
     */
    private void settingsStatusBar(Window mWindow) {
        final int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        mWindow.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        mWindow.setStatusBarColor(Color.TRANSPARENT);
        mWindow.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        new WindowInsetsControllerCompat(mWindow, mWindow.getDecorView()).setAppearanceLightStatusBars(currentNightMode == Configuration.UI_MODE_NIGHT_NO);
    }


    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!notePresenter.getExitNoteSave() && binding.valueNote.getText().toString().trim().length() >= 2) {
            saveNote(false);
        }

    }


    @Override
    public void initParam() {

    }

    @Override
    public void initTypeActivity() {
        if (notePresenter.getNewNotesKey()) {
            activatedActivity();
            if (notePresenter.getTagNote().length() >= 2)
                changeTag(notePresenter.getTagNote(), false);
            binding.titleToolbarData.setText(getString(R.string.lastDateEditNote, lastDayEditNote(new Date().getTime())));

            if (notePresenter.getShareText() != null && notePresenter.getShareText().length() > 5)
                binding.valueNote.setText(notePresenter.getShareText());
        } else if (notePresenter.getIdKey() >= 1) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
            notePresenter.loadingData(notePresenter.getIdKey());
        }
    }


    @Override
    public void initListeners() {
        binding.notesTitle.addTextChangedListener(new TextWatcher() {
            @Override
            protected void changeText(Editable s) {
                if (s.toString().contains("\n")) {
                    binding.notesTitle.setText(s.toString().replace('\n', ' ').trim());
                    binding.valueNote.requestFocus();
                }
            }
        });

        binding.titleToolbarTag.setOnClickListener(v -> openPopupWindowsTag());
    }

    @Override
    public void editIdNoteCreated(long idNote) {
        notePresenter.getNote().setId(Math.toIntExact(idNote));
    }


    @Override
    public void settingsActionBar() {
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
    }


    @Override
    public void activatedActivity() {

        binding.setActivateEdit(true);
        binding.valueNote.setEnabled(true);
        binding.valueNote.setFocusable(true);
        if (!notePresenter.getNewNotesKey())
            binding.valueNote.setSelection(binding.valueNote.getText().length());
        binding.valueNote.setFocusableInTouchMode(true);
        binding.valueNote.requestFocus();

        if (notePresenter.getNewNotesKey()) {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).toggleSoftInputFromWindow(binding.valueNote.getApplicationWindowToken(), InputMethodManager.SHOW_IMPLICIT, 0);

        } else {
            if (binding.valueNote.requestFocus()) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(binding.valueNote, InputMethodManager.SHOW_IMPLICIT);
            }
        }


    }


    @Override
    public void onRestart() {
        super.onRestart();
    }

    @Override
    public void onBackPressed() {
        notePresenter.closeActivity();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_toolbar_note, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            notePresenter.closeActivity();

        }
        if (item.getItemId() == R.id.moreBut) {
            if (!notePresenter.getNewNotesKey()) saveNote(true);
            new MoreNoteDialog(notePresenter.getNewNotesKey() ? new Note().create(binding.notesTitle.getText().toString(), binding.valueNote.getText().toString(), new Date().getTime()) : notePresenter.getNote(), notePresenter.getNewNotesKey(), true, 0).show(getSupportFragmentManager(), "MoreNote");

        }


        return true;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        notePresenter.detachView();
        binding.notesTitle.addTextChangedListener(null);
        binding.titleToolbarTag.setOnClickListener(null);
    }


    @Override
    public void loadingNote(Note note) {
        binding.notesTitle.setText(note.getTitle().length() >= 1 ? note.getTitle() : "");
        binding.valueNote.setText(note.getValue() == null ? "" : note.getValue());
        binding.valueNote.setMovementMethod(new CustomLinkMovementMethod() {
            @Override
            protected void onClickLink(String link, int type) {
                link = link.replaceAll("mailto:", "").replaceAll("tel:", "");

                new LinkInfoDialog(link, type).show(getSupportFragmentManager(), "LinkInfoDialog");

            }

        });
        binding.titleToolbarData.setText(getString(R.string.lastDateEditNote, lastDayEditNote(note.getDate())));
        changeTag(note.getTag(), false);
    }


    @Override
    public void closeNoteActivity() {
        binding.getRoot().clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(binding.valueNote.getWindowToken(), 0);

        notePresenter.setExitNoSave(true);
        if (binding.valueNote.getText().toString().trim().length() >= 2) saveNote(false);
        if (notePresenter.getShareText().length() >= 2)
            Toast.makeText(this, getString(R.string.noteSaved), Toast.LENGTH_SHORT).show();
        supportFinishAfterTransition();
    }

    private void saveNote(boolean saveLocal) {
        long mThisDate = new Date().getTime();
        String mTitle = binding.notesTitle.getText().toString();
        String mValue = binding.valueNote.getText().toString();

        String mNoteValue = "";

        if (!notePresenter.getNewNotesKey())
            mNoteValue = notePresenter.getNote().getValue() == null ? "" : notePresenter.getNote().getValue();

        if (notePresenter.getNewNotesKey()) {
            Note note = new Note().create(mTitle.length() >= 1 ? mTitle : "", mValue, mThisDate, notePresenter.getTagNote());
            notePresenter.setNote(note);
            notePresenter.createNote(note);
            notePresenter.setNewNoteKey(false);

        } else {
            if (saveNoteToLocal(mValue, mTitle, mNoteValue, mThisDate) && !saveLocal) {
                notePresenter.saveNote(notePresenter.getNote());
            }
        }
        Log.wtf(TAG, "saveNote: ");
    }


    private boolean saveNoteToLocal(String mValue, String mTitle, String mNoteValue, long mThisDate) {
        Log.wtf(TAG, "saveNoteToLocal: ");
        if (!mValue.equals(mNoteValue) || !mTitle.equals(notePresenter.getNote().getTitle())) {
            boolean x1 = false;
            if (!notePresenter.getNote().getTitle().contentEquals(mTitle)) {
                notePresenter.getNote().setTitle(mTitle);
                x1 = true;
            }
            if (!mNoteValue.contentEquals(mValue)) {
                notePresenter.getNote().setValue(mValue);
                x1 = true;
            }

            if (x1) {
                notePresenter.getNote().setDate(mThisDate);
                return true;
            }

        }
        return false;
    }

    @Override
    public void closeActivityNotSaved() {
        notePresenter.setExitNoSave(true);
        finish();
    }

    @Override
    public void changeTag(String nameTag, boolean change) {
        if (change) {
            notePresenter.getNote().setTag(nameTag);
            notePresenter.setTagNote(nameTag);
        }
        if (nameTag.length() >= 1) {
            binding.titleToolbarTag.setText(getString(R.string.tagHastag, nameTag));
            binding.titleToolbarTag.setVisibility(View.VISIBLE);
        } else {
            binding.titleToolbarTag.setVisibility(View.GONE);
        }
    }

    private void openPopupWindowsTag() {
        String noteTag = notePresenter.getTagNote().length() == 0 ? notePresenter.getNote().getTag() : notePresenter.getTagNote();
        if (noteTag.length() != 0) {
            new PopupWindowsTagNote(getLayoutInflater(), binding.titleToolbarTag, () -> {

                finish();
                startActivity(new Intent(NoteActivity.this, NoteActivity.class).putExtra("NewNote", true).putExtra("tagNote", noteTag));


            });
        }
    }

    @Override
    public void openCopyNote(long idNote) {
        finish();
        startActivity(new Intent(NoteActivity.this, NoteActivity.class).putExtra("NewNote", false).putExtra("idNote", idNote).putExtra("shareText", "").putExtra("tagNote", ""));
    }


    @Override
    public void changeTextStyle() {
        binding.valueNote.setTypeface(null, notePresenter.getTypeFace(notePresenter.getDataManager().getTypeFaceNoteActivity()));
    }

    @Override
    public void changeTextSizeOnline(int sizeText) {
        binding.valueNote.setTextSize(sizeText == 0 ? 16 : sizeText);
        binding.notesTitle.setTextSize(sizeText == 0 ? 20 : sizeText + 4);
    }

    @Override
    public void changeTextSizeOffline() {
        changeTextSizeOnline(notePresenter.getDataManager().getSizeTextNoteActivity());
    }


    @Override
    public void createShortCut() {
        onInfoSnack(R.string.addShortCutSuccessfully, binding.noteLayout, SnackBarInfo.Info, Snackbar.LENGTH_LONG);
    }

    @Override
    public void shortCutDouble() {
        onInfoSnack(R.string.shortCutCreateFallDouble, binding.noteLayout, SnackBarInfo.Info, Snackbar.LENGTH_LONG);
    }

}
