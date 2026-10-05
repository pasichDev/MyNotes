package com.pasich.mynotes.extendedEditor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.core.view.ViewCompat;
import com.google.android.material.color.MaterialColors;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;
import com.pasich.mynotes.extendedEditor.models.PickedFile;
import com.pasich.mynotes.extendedEditor.utils.EditorAttachmentsWebViewClient;
import com.pasich.mynotes.extendedEditor.utils.EditorJSInterface;
import com.pasich.mynotes.extendedEditor.utils.SettingsEditorColors;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        webView.loadUrl(
                "file:///android_asset/editor/editor.html?locale="
                        + Locale.getDefault().getLanguage());
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
        editorInterface.toggleReadMode();
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
        editorInterface.loadNoteToEditor(note, restoreAnchorIndex, restoreAnchorOffset);
        restoreAnchorIndex = -1;
        restoreAnchorOffset = 0;
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
     * Fully and safely destroys the WebView instance to prevent memory leaks. Must be called from
     * Activity/Fragment onDestroy().
     */
    public void release() {
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

        if (editorInterface != null) editorInterface.release();
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
