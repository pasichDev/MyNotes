package com.pasich.mynotes.extendedEditor;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.color.MaterialColors;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;
import com.pasich.mynotes.extendedEditor.models.PickedFile;
import com.pasich.mynotes.extendedEditor.utils.EditorAttachmentsWebViewClient;
import com.pasich.mynotes.extendedEditor.utils.EditorJSInterface;
import com.pasich.mynotes.extendedEditor.utils.SettingsEditorColors;
import com.pasich.mynotes.utils.editor.KeyboardRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongFunction;

public class NoteEditorView extends FrameLayout {

    public static final int FILE_CHOOSER_REQUEST = 2025;
    private static final String TAG = "NoteEditorView";
    private WebView webView;
    private View loader;
    private EditorJSInterface editorInterface;
    private Handler handler;
    private Note pendingNote;
    // Block at the top of the viewport, as last reported by the editor.
    private int anchorIndex = -1;
    private int anchorOffset = 0;
    // Reading position to restore on the next load (after a recreation).
    private int restoreAnchorIndex = -1;
    private int restoreAnchorOffset = 0;
    // A saved position resolved for the next load, in the page's form.
    private String restoreViewState;
    // Where the note is being read or edited, as last reported by the page; null until the page
    // has shown the note and applied any saved position.
    private String lastViewState;
    private boolean autofocus = true;
    private boolean startReadOnly = false;
    private boolean doubleTapToEdit = false;
    // Put the caret at the start of the next loaded note ("open in editing mode").
    private boolean focusStart = false;
    private boolean editorIsReady = false;
    private boolean htmlLoaded = false;
    private OnFileChooserListener fileChooserListener;
    private OnContextDialogListener onContextDialogListener;
    private ValueCallback<Uri[]> fileCallback;
    // What the open picker was asked for and where its block goes. Kept in the activity's saved
    // state, so a result that comes back to a recreated screen still lands in the right place.
    private String chooserKind;
    private int chooserBlockIndex = -1;
    // Files picked for a page that no longer exists, inserted once the note is rendered again.
    private final List<PickedFile> orphanedPicks = new ArrayList<>();
    private String orphanedKind;
    private int orphanedIndex = -1;
    private boolean noteRendered = false;
    // Undo history handed over by the page of the screen this one replaced; asked once, for the
    // first note loaded.
    private LongFunction<String> handedOverHistory;
    private boolean releasing = false;
    private final KeyboardRequest keyboardRequest = new KeyboardRequest();
    private final Runnable keyboardStep = this::continueKeyboardRequest;

    public NoteEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void setOnContextDialogListener(OnContextDialogListener l) {
        this.onContextDialogListener = l;
    }

    private void init(Context context) {
        inflate(context, R.layout.view_note_editor, this);

        webView = findViewById(R.id.editorWebView);
        loader = findViewById(R.id.editorLoader);
        handler = new Handler(Looper.getMainLooper());
        ViewCompat.setNestedScrollingEnabled(webView, false);

        setupWebView();
    }

    /**
     * Loads the editor HTML once the view is attached to window. This ensures WebView is fully
     * initialized before loading local assets.
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!htmlLoaded) {
            htmlLoaded = true;
            webView.post(this::loadEditorHtml);
        }
    }

    /** Loads the Editor.js HTML page from the app assets with the current locale. */
    private void loadEditorHtml() {
        if (webView == null) return;
        // The app's language, which the page's hints and tool names follow.
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        String url =
                "file:///android_asset/editor/editor.html?locale="
                        + (locale != null ? locale : Locale.getDefault()).getLanguage();
        if (!autofocus) url += "&autofocus=0";
        if (startReadOnly) url += "&readonly=1";
        if (doubleTapToEdit) url += "&dbltap=1";
        webView.loadUrl(url);
    }

    /**
     * How the page starts: in reading mode or not, and whether a double tap in reading mode starts
     * editing there. Takes effect only before the view is attached.
     */
    public void setStartOptions(boolean readOnly, boolean doubleTapToEdit) {
        this.startReadOnly = readOnly;
        this.doubleTapToEdit = doubleTapToEdit;
    }

    /** Puts the caret at the start of the next note loaded, unless a saved position places it. */
    public void setFocusStart(boolean focusStart) {
        this.focusStart = focusStart;
    }

    /**
     * Whether the editor puts the caret in the first block as it starts; on unless a saved position
     * is about to put it back. Takes effect only before the view is attached.
     */
    public void setAutofocus(boolean autofocus) {
        this.autofocus = autofocus;
    }

    /**
     * Configures WebView, JS bridge, security settings and file chooser. Called once during
     * initialization.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setGeolocationEnabled(false);
        webSettings.setAllowUniversalAccessFromFileURLs(false);
        webSettings.setOffscreenPreRaster(true);

        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setLayerType(View.LAYER_TYPE_NONE, null);

        // The page is transparent until the theme reaches it; paint the theme's surface from the
        // first frame so the editor never flashes white, least of all in the dark theme.
        webView.setBackgroundColor(
                MaterialColors.getColor(
                        webView, com.google.android.material.R.attr.colorSurfaceContainerLow));

        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setWebViewClient(new EditorAttachmentsWebViewClient(getContext()));
        webView.setWebChromeClient(
                new WebChromeClient() {
                    @Override
                    public boolean onShowFileChooser(
                            WebView webView,
                            ValueCallback<Uri[]> filePathCallback,
                            FileChooserParams fileChooserParams) {
                        if (fileChooserListener == null) return false;
                        Intent intent;
                        try {
                            intent = fileChooserParams.createIntent();
                        } catch (Exception e) {
                            return false;
                        }
                        fileCallback = filePathCallback;
                        chooserKind = kindFor(fileChooserParams.getAcceptTypes());
                        chooserBlockIndex = -1;
                        // Remember which block asked before the picker covers the screen, then
                        // open it.
                        webView.evaluateJavascript(
                                "window.currentBlockIndex ? currentBlockIndex() : -1",
                                value -> {
                                    chooserBlockIndex = parseIndex(value);
                                    if (fileChooserListener != null) {
                                        fileChooserListener.onOpenFileChooser(
                                                intent, FILE_CHOOSER_REQUEST);
                                    }
                                });
                        return true;
                    }
                });

        webView.setOnLongClickListener(
                v -> {
                    if (onContextDialogListener != null) {
                        onContextDialogListener.openContextCopy();
                    } else {
                        Log.w(TAG, "OnContextDialogListener is null");
                    }
                    return true;
                });
    }

    public WebView getWebView() {
        return webView;
    }

    private static String kindFor(String[] acceptTypes) {
        if (acceptTypes != null) {
            for (String type : acceptTypes) {
                if (type != null && type.startsWith("image/")) return EditorJSInterface.KIND_IMAGE;
            }
        }
        return EditorJSInterface.KIND_FILE;
    }

    private static int parseIndex(String value) {
        try {
            return value == null ? -1 : (int) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public void onEditorReadyFromBridge() {
        handler.post(
                () -> {
                    editorIsReady = true;
                    applyTheme();
                    if (pendingNote != null) {
                        if (editorInterface != null) {
                            loadIntoEditor(pendingNote);
                        } else {
                            Log.w(
                                    TAG,
                                    "onEditorReady: editorInterface is null, dropping pending note");
                        }
                        pendingNote = null;
                    }
                    handler.postDelayed(this::showEditor, 120);
                });
    }

    public void setEditorInterface(EditorJSInterface editorInterface) {
        this.editorInterface = editorInterface;
        if (webView != null) {
            webView.addJavascriptInterface(editorInterface, EditorJSInterface.nameInterface);
        }
    }

    /** Applies Android theme colors to the editor via JS bridge. */
    public void applyTheme() {
        if (editorInterface != null) {
            editorInterface.setThemeColors(new SettingsEditorColors().getThemeColors(getContext()));
        }
    }

    void showEditor() {
        if (webView == null || loader == null) return;
        loader.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> loader.setVisibility(View.GONE))
                .start();
        webView.setAlpha(0f);
        webView.setVisibility(View.VISIBLE);
        webView.animate().alpha(1f).setDuration(400).setStartDelay(100).start();
    }

    /** Toggles read-only mode inside Editor.js (title and blocks become non-editable). */
    public void actionRead() {
        if (!editorIsReady) return;
        keyboardRequest.cancel();
        editorInterface.toggleReadMode();
    }

    /** Whether the page has finished starting, so a mode switch can reach it. */
    public boolean isEditorReady() {
        return editorIsReady;
    }

    /**
     * Focuses the page and opens the keyboard on its caret, giving the page a caret first when it
     * has none. The keyboard is asked for only once the page's editable field holds the focus, and
     * is then connected to it afresh: asked for earlier, it can come up attached to nothing. A
     * request made while the screen is still opening waits for window focus, and is repeated until
     * the keyboard is up ({@link KeyboardRequest}).
     */
    public void showKeyboard() {
        if (webView == null || releasing) return;
        keyboardRequest.start();
        handler.removeCallbacks(keyboardStep);
        continueKeyboardRequest();
    }

    private void continueKeyboardRequest() {
        if (webView == null || releasing) return;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(webView);
        boolean visible = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
        // The WebView reports a text editor only while an editable field of the page is focused.
        boolean editorFocused = webView.hasFocus() && webView.onCheckIsTextEditor();
        InputMethodManager imm =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        switch (keyboardRequest.next(visible, webView.hasWindowFocus(), editorFocused)) {
            case DONE, WAIT_FOR_WINDOW_FOCUS -> {
                // Done, or continued from onWindowFocusChanged.
            }
            case FOCUS_AND_RETRY -> {
                // The view first, so the page is focused when it moves the focus to its field.
                if (!webView.hasFocus()) webView.requestFocus();
                if (editorInterface != null) editorInterface.focusCaret();
                handler.postDelayed(keyboardStep, KeyboardRequest.RETRY_MS);
            }
            case CONNECT_AND_SHOW -> {
                if (imm != null) imm.restartInput(webView);
                askForKeyboard(imm);
            }
            case SHOW_AND_RETRY -> askForKeyboard(imm);
        }
    }

    private void askForKeyboard(@Nullable InputMethodManager imm) {
        Window window = findWindow();
        if (window != null) {
            WindowCompat.getInsetsController(window, webView).show(WindowInsetsCompat.Type.ime());
        }
        if (imm != null) imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT);
        handler.postDelayed(keyboardStep, KeyboardRequest.RETRY_MS);
    }

    @Nullable
    private Window findWindow() {
        Context context = getContext();
        while (context instanceof ContextWrapper wrapper) {
            if (context instanceof Activity activity) return activity.getWindow();
            context = wrapper.getBaseContext();
        }
        return null;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && keyboardRequest.isPending()) {
            handler.removeCallbacks(keyboardStep);
            continueKeyboardRequest();
        }
    }

    /** Takes back the last change in the note; the page saves the result like any edit. */
    public void undo() {
        if (editorIsReady && editorInterface != null) editorInterface.undo();
    }

    /** Applies the last undone change again. */
    public void redo() {
        if (editorIsReady && editorInterface != null) editorInterface.redo();
    }

    public void deleteBlock(String blockId, String fileUrl) {
        if (!editorIsReady) return;
        editorInterface.deleteAttachmentBlockRequest(blockId, fileUrl);
    }

    /**
     * Loads a note into the editor. If the editor is not ready yet, the note is stored temporarily.
     */
    public void load(Note mNote) {
        if (mNote == null) {
            pendingNote = null;
            return;
        }

        if (editorIsReady) {
            loadIntoEditor(mNote);
        } else {
            pendingNote = mNote;
        }
    }

    private void loadIntoEditor(Note note) {
        lastViewState = null;
        LongFunction<String> history = handedOverHistory;
        handedOverHistory = null;
        editorInterface.loadNoteToEditor(
                note,
                restoreAnchorIndex,
                restoreAnchorOffset,
                restoreViewState,
                focusStart,
                history != null ? history.apply(note.getId()) : null);
        restoreAnchorIndex = -1;
        restoreAnchorOffset = 0;
        restoreViewState = null;
        focusStart = false;
    }

    /**
     * Where the undo history of the page this screen replaced can be found, by note id; asked when
     * the first note is loaded.
     */
    public void setHandedOverHistory(LongFunction<String> source) {
        handedOverHistory = source;
    }

    /**
     * A saved position to apply once the next note is rendered ({@code
     * ExtendedViewStateJson.toPage}); it takes the place of the reading anchor.
     */
    public void setRestoreViewState(String viewState) {
        restoreViewState = viewState;
    }

    /** Remembers where the note is being read or edited, as reported by the page. */
    public void onViewState(String json) {
        lastViewState = json;
    }

    /**
     * Asks the page where the note is right now, without waiting for its next report; {@code
     * callback} gets the page's JSON, or null when it has none. The answer is also kept as the last
     * position.
     */
    public void readViewState(@NonNull ValueCallback<String> callback) {
        if (!editorIsReady || editorInterface == null || releasing) {
            callback.onReceiveValue(null);
            return;
        }
        editorInterface.readViewState(
                json -> {
                    if (json != null) lastViewState = json;
                    callback.onReceiveValue(json);
                });
    }

    /** The last position the page reported, or null when it has not reported one yet. */
    public String getLastViewState() {
        return lastViewState;
    }

    /** Remembers the block at the top of the viewport, as reported by the editor. */
    public void onViewportAnchor(int blockIndex, int offsetPx) {
        anchorIndex = blockIndex;
        anchorOffset = offsetPx;
    }

    public int getAnchorIndex() {
        return anchorIndex;
    }

    public int getAnchorOffset() {
        return anchorOffset;
    }

    /** Reading position to scroll to once the next note has been rendered. */
    public void setRestoreAnchor(int blockIndex, int offsetPx) {
        restoreAnchorIndex = blockIndex;
        restoreAnchorOffset = offsetPx;
        anchorIndex = blockIndex;
        anchorOffset = offsetPx;
    }

    /** Asks the editor for its document right away instead of after its change batching. */
    public void requestFlush() {
        if (editorIsReady && editorInterface != null) editorInterface.requestFlush();
    }

    /**
     * Handles result from WebView file chooser, including validation (size limit, free space),
     * before passing the file to JS.
     */
    public void onFileChooserResult(int resultCode, Intent data) {
        Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        String kind = chooserKind;
        int index = chooserBlockIndex;
        chooserKind = null;
        chooserBlockIndex = -1;

        if (result != null && result.length > 0) {

            Uri uri = result[0];

            AttachmentStorage.AttachmentValidationResult validation =
                    AttachmentStorage.validateBeforeAttach(getContext(), uri);

            if (!validation.ok) {
                Toast.makeText(getContext(), validation.error, Toast.LENGTH_SHORT).show();
                result = null;
            }
        }

        List<PickedFile> picked = new ArrayList<>();
        if (result != null) {
            for (Uri uri : result) {
                picked.add(
                        new PickedFile(
                                uri,
                                AttachmentStorage.getDisplayName(getContext(), uri),
                                AttachmentStorage.getFileSize(getContext(), uri)));
            }
        }

        if (fileCallback == null) {
            // The screen was recreated while the picker was open: the page that asked is gone.
            // Store the file and put its block back where the old page had it.
            if (!picked.isEmpty() && kind != null) {
                orphanedPicks.clear();
                orphanedPicks.addAll(picked);
                orphanedKind = kind;
                orphanedIndex = index;
                insertOrphanedPicks();
            }
            return;
        }

        if (editorInterface != null) editorInterface.offerPickedFiles(picked);
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    /** Called when the page has rendered a note; picks waiting for it are inserted now. */
    public void onNoteRenderedFromBridge() {
        handler.post(
                () -> {
                    noteRendered = true;
                    insertOrphanedPicks();
                });
    }

    private void insertOrphanedPicks() {
        if (!noteRendered || editorInterface == null || orphanedPicks.isEmpty()) return;
        String kind = orphanedKind;
        int index = orphanedIndex;
        for (PickedFile file : orphanedPicks) {
            editorInterface.uploadAndInsert(
                    kind,
                    file,
                    index,
                    () ->
                            Toast.makeText(
                                            getContext(),
                                            R.string.attachment_save_failed,
                                            Toast.LENGTH_SHORT)
                                    .show());
            if (index >= 0) index++;
        }
        orphanedPicks.clear();
    }

    /** The open picker's kind, to keep in the saved state; null when no picker is open. */
    public String getChooserKind() {
        return chooserKind;
    }

    public int getChooserBlockIndex() {
        return chooserBlockIndex;
    }

    /** Restores what a picker opened by the previous instance was asked for. */
    public void restoreChooserState(String kind, int blockIndex) {
        chooserKind = kind;
        chooserBlockIndex = blockIndex;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    /**
     * Destroys the WebView once the page has handed over its last edit. Must be called from
     * Activity/Fragment onDestroy().
     */
    public void release() {
        release(false);
    }

    /**
     * Destroys the WebView once the page has handed over its last edit and, with {@code
     * keepHistory} (the screen is being recreated), its undo history. The page answers
     * asynchronously, so the WebView is kept until it has, for a short time at most; destroying it
     * at once lost the last edit and made a flush still on its way reach a destroyed WebView.
     */
    public void release(boolean keepHistory) {
        if (releasing) return;
        releasing = true;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (editorIsReady && editorInterface != null && webView != null) {
            editorInterface.finishPage(keepHistory, this::destroyWebView);
        } else {
            destroyWebView();
        }
    }

    private void destroyWebView() {
        // Before the WebView goes: nothing still queued may reach it afterwards.
        if (editorInterface != null) editorInterface.release();
        try {
            if (webView != null) {

                ViewParent parent = webView.getParent();
                if (parent instanceof ViewGroup vg) {
                    vg.removeView(webView);
                }

                webView.stopLoading();
                webView.loadUrl("about:blank");

                webView.clearHistory();
                webView.clearCache(true);
                webView.removeAllViews();
                webView.removeJavascriptInterface(EditorJSInterface.nameInterface);
                webView.setWebChromeClient(null);
                webView.setWebViewClient(null);

                webView.destroy();
                webView = null;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error while destroying WebView", t);
        }

        editorInterface = null;

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    /**
     * Soft refresh animation: - shows loader for 500ms - fades out and fades in WebView Does NOT
     * reload HTML or reset scroll.
     */
    public void softRefresh() {
        if (webView == null || loader == null) return;

        // Show loader
        loader.setAlpha(0f);
        loader.setVisibility(View.VISIBLE);
        loader.animate().alpha(1f).setDuration(150).start();

        // Hide editor smoothly
        webView.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(
                        () -> {
                            handler.postDelayed(
                                    () -> {

                                        // Hide loader
                                        loader.animate()
                                                .alpha(0f)
                                                .setDuration(200)
                                                .withEndAction(
                                                        () -> loader.setVisibility(View.GONE))
                                                .start();

                                        // Show editor again
                                        webView.animate().alpha(1f).setDuration(200).start();
                                    },
                                    500); // loader visible ~0.5 sec
                        })
                .start();
    }

    public void setOnFileChooserListener(OnFileChooserListener l) {
        this.fileChooserListener = l;
    }

    public interface OnContextDialogListener {
        void openContextCopy();
    }

    public interface OnFileChooserListener {
        void onOpenFileChooser(Intent intent, int requestCode);
    }
}
