package com.pasich.mynotes.ui.view.activity;

import static android.view.View.VISIBLE;
import static com.pasich.mynotes.utils.FormattedDataUtil.lastDayEditNote;
import static com.pasich.mynotes.utils.transition.TransitionUtil.buildContainerTransform;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.ActivityNoteExtendedEditorBinding;
import com.pasich.mynotes.ui.contract.NoteContract;
import com.pasich.mynotes.ui.presenter.NotePresenter;
import com.pasich.mynotes.ui.view.dialogs.MoreNoteDialog;
import com.pasich.mynotes.utils.constants.NameTransition;
import com.pasich.mynotes.utils.enums.SaveState;
import com.pasich.mynotes.utils.noteEditor.EditorJSInterface;
import com.pasich.mynotes.utils.noteEditor.EditorJsonUtils;
import com.pasich.mynotes.utils.noteEditor.SettingsEditorColors;

import java.util.Locale;
import java.util.Objects;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class NoteExtendedEditorActivity extends BaseActivity implements NoteContract.view {

    public ActivityNoteExtendedEditorBinding binding;
    @Inject
    public NoteContract.presenter notePresenter;

    // Menu for the save status indicator
    private MenuItem saveStatusMenuItem;

    private EditorJSInterface editorInterface;
    private Handler mainHandler;

    private boolean isReadMode = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        selectTheme();
        settingsStatusBar(getWindow());
        long idNote = getIntent().getLongExtra("idNote", 0);

        binding = ActivityNoteExtendedEditorBinding.inflate(getLayoutInflater());
        mainHandler = new Handler(Looper.getMainLooper());
        binding.noteLayout.setTransitionName(idNote == 0 ? NameTransition.fabTransaction : String.valueOf(idNote));
        setEnterSharedElementCallback(new MaterialContainerTransformSharedElementCallback());
        getWindow().setSharedElementEnterTransition(buildContainerTransform(binding.noteLayout));
        getWindow().setSharedElementReturnTransition(buildContainerTransform(binding.noteLayout));

        super.onCreate(savedInstanceState);
        setContentView(binding.getRoot());

        setupEdgeToEdgeInsetsWithKeyboard(binding.getRoot());
        binding.setPresenter((NotePresenter) notePresenter);
        notePresenter.attachView(this);
        notePresenter.getLoadIntentData(getIntent());
        notePresenter.viewIsReady();
        notePresenter.setExtendedEditor(true);

        setupRichEditor();

        // Handle back button press with OnBackPressedDispatcher
        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                notePresenter.closeActivity();
            }
        });
    }


    void showEditor() {
        binding.editorLoader.animate().alpha(0f).setDuration(300).withEndAction(() -> binding.editorLoader.setVisibility(View.GONE)).start();
        binding.richEditor.setAlpha(0f);
        binding.richEditor.setVisibility(View.VISIBLE);
        binding.richEditor.animate().alpha(1f).setDuration(400).setStartDelay(100).start();
    }

    /**
     * Setup WebView with Editor.js
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void setupRichEditor() {
        // Resize WebView
        binding.richEditor.post(() -> {
            binding.richEditor.getLayoutParams().height = ((View) binding.richEditor.getParent()).getHeight() - findViewById(R.id.toolbar).getHeight() - 16;
            binding.richEditor.requestLayout();
        });

        // Configure WebView settings
        WebSettings webSettings = binding.richEditor.getSettings();
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Clean scroll
        binding.richEditor.setVerticalScrollBarEnabled(false);
        binding.richEditor.setHorizontalScrollBarEnabled(false);
        binding.richEditor.setOverScrollMode(View.OVER_SCROLL_NEVER);


        // Setup JavaScript interface
        editorInterface = new EditorJSInterface(new EditorJSInterface.EditorListener() {
            @Override
            public void onEditorReady() {
                mainHandler.post(() -> {
                    editorInterface.setThemeColors(new SettingsEditorColors().getThemeColors(NoteExtendedEditorActivity.this));
                    if (!notePresenter.getNewNotesKey()) {
                        editorInterface.loadNoteToEditor(notePresenter.getNote());
                    }
                    showEditor();
                });
            }

            @Override
            public void onContentChanged(String jsonData) {
                mainHandler.post(() -> {
                    // Update note with both formats
                    if (notePresenter != null && notePresenter.getNote() != null) {
                        processTextChange(jsonData);
                    }
                });
            }

            @Override
            public void onTitleChanged(String title) {
                if (notePresenter != null && notePresenter.getNote() != null) {
                    processTitleChange(title);
                }
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> Log.e("NoteActivityBeta", "Editor error: " + error));
            }
        }, binding.richEditor);


        binding.richEditor.addJavascriptInterface(editorInterface, EditorJSInterface.nameInterface);

        // Setup WebView client
        binding.richEditor.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.e("NoteActivityBeta", "WebView error: " + error.getDescription());
            }
        });


        // Load Editor.js HTML, add local
        String url = "file:///android_asset/editor/note_editor.html?locale=" + Locale.getDefault().getLanguage();
        binding.richEditor.loadUrl(url);

    }

    /**
     * Налаштовує відступи з урахуванням клавіатури для NoteActivity
     */
    private void setupEdgeToEdgeInsetsWithKeyboard(View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            // Отримуємо відступи для системних барів
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Встановлюємо padding зверху для системних барів тільки для кореневого view
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), 0);

            return insets;
        });
    }

    private void settingsStatusBar(Window window) {
        // Декор під edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // Контролюємо колір іконок статусбару залежно від нічного режиму
        final int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(window, window.getDecorView());
        insetsController.setAppearanceLightStatusBars(currentNightMode == Configuration.UI_MODE_NIGHT_NO);

        // Прозорий статусбар
        window.setStatusBarColor(Color.TRANSPARENT);

    }

    @Override
    protected void onStart() {
        super.onStart();


    }


    @Override
    public void initParam() {
        // Not implemented
    }

    @Override
    public void initTypeActivity() {
        if (notePresenter.getNewNotesKey()) {
            if (notePresenter.getTagNote().length() >= 2)
                changeTag(notePresenter.getTagNote(), false);

            binding.titleToolbarDataCollapsed.setText(getString(R.string.new_note));
            if (notePresenter.getShareText() != null && notePresenter.getShareText().length() > 5)
                activatedActivity();
        } else if (notePresenter.getIdKey() >= 1) {
            notePresenter.loadingData(notePresenter.getIdKey());
        }
    }

    @Override
    public void initListeners() {
        // Not implemented
    }

    /**
     * Process title changes with enhanced features
     */
    private void processTitleChange(String title) {
        notePresenter.getNote().setTitle(title);
        notePresenter.getNote().setHasRichContent(true);
        notePresenter.onTextChanged();
    }

    /**
     * Process text changes with enhanced features
     */
    private void processTextChange(String jsonData) {
        notePresenter.getNote().setValue(EditorJsonUtils.jsonToPlainText(jsonData));
        notePresenter.getNote().setValueJson(jsonData);
        notePresenter.getNote().setHasRichContent(true);
        notePresenter.onTextChanged();
    }

    @Override
    public void editIdNoteCreated(long idNote) {
        binding.titleToolbarDataCollapsed.setText(getString(R.string.lastDateEditNote, lastDayEditNote(notePresenter.getNote().getDate())));
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
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_toolbar_note_extendes, menu);
        saveStatusMenuItem = menu.findItem(R.id.saveStatusBut);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            notePresenter.closeActivity();

        }
        if (item.getItemId() == R.id.moreBut) {
            new MoreNoteDialog(notePresenter.getNote(), notePresenter.getNewNotesKey(), true, 0, true).show(getSupportFragmentManager(), "MoreNote");
        }

        if (item.getItemId() == R.id.actionRead) {
            isReadMode = !isReadMode;
            runOnUiThread(() -> binding.richEditor.evaluateJavascript("toggleReadModeFromAndroid();", null));
            item.setIcon(isReadMode ? R.drawable.ic_edit : R.drawable.ic_read);
            return true;
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
        // Clear WebView resources
        binding.richEditor.removeJavascriptInterface("Android");
        binding.richEditor.loadUrl("about:blank");
        binding.richEditor.destroy();

        if (notePresenter != null) {
            ((NotePresenter) notePresenter).cleanupHandlers();
            notePresenter.detachView();
        }

        if (binding != null) {
            binding.titleToolbarTagCollapsed.setOnClickListener(null);
        }

        // Clear handlers
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }

    }

    @Override
    public void loadingNote(Note note) {
        if (note == null) {
            Log.e("NoteActivityBeta", "Received null note in loadingNote()");
            return;
        }

        // Безпечна перевірка tag перед викликом changeTag
        String tag = note.getTag();
        changeTag(tag != null ? tag : "", false);

        binding.titleToolbarDataCollapsed.setText(getString(R.string.lastDateEditNote, lastDayEditNote(note.getDate())));
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
        finish();
        startActivity(new Intent(NoteExtendedEditorActivity.this, NoteExtendedEditorActivity.class).putExtra("NewNote", false).putExtra("idNote", idNote).putExtra("shareText", "").putExtra("tagNote", "").putExtra("betaMode", true));


    }

    @Override
    public void changeTextStyle() {
        // Not implemented
    }

    @Override
    public void changeTextSizeOnline(int sizeText) {
        // Not implemented
    }

    @Override
    public void changeTextSizeOffline() {
        // Not implemented
    }
}