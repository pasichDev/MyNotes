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

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;
import com.pasich.mynotes.extendedEditor.utils.EditorAttachmentsWebViewClient;
import com.pasich.mynotes.extendedEditor.utils.EditorJSInterface;
import com.pasich.mynotes.extendedEditor.utils.SettingsEditorColors;

import java.util.Locale;

public class NoteEditorView extends FrameLayout {

    public static final int FILE_CHOOSER_REQUEST = 2025;
    private static final String TAG = "NoteEditorView";
    private WebView webView;
    private View loader;
    private EditorJSInterface editorInterface;
    private Handler handler;
    private Note pendingNote;
    private boolean editorIsReady = false;
    private boolean htmlLoaded = false;
    private OnFileChooserListener fileChooserListener;
    private OnContextDialogListener onContextDialogListener;
    private ValueCallback<Uri[]> fileCallback;

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
     * Loads the editor HTML once the view is attached to window.
     * This ensures WebView is fully initialized before loading local assets.
     */

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!htmlLoaded) {
            htmlLoaded = true;
            webView.post(this::loadEditorHtml);
        }
    }

    /**
     * Loads the Editor.js HTML page from the app assets with the current locale.
     */

    private void loadEditorHtml() {
        webView.loadUrl(
                "file:///android_asset/editor/editor.html?locale=" + Locale.getDefault().getLanguage()
        );
    }

    /**
     * Configures WebView, JS bridge, security settings and file chooser.
     * Called once during initialization.
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

        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setWebViewClient(new EditorAttachmentsWebViewClient(getContext()));
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                fileCallback = filePathCallback;
                Intent intent;
                try {
                    intent = fileChooserParams.createIntent();
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }

                if (fileChooserListener != null) {
                    fileChooserListener.onOpenFileChooser(intent, FILE_CHOOSER_REQUEST);
                    return true;
                }

                return false;
            }

        });

        webView.setOnLongClickListener(v -> {
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

    public void onEditorReadyFromBridge() {
        handler.post(() -> {
            editorIsReady = true;
            applyTheme();
            if (pendingNote != null) {
                editorInterface.loadNoteToEditor(pendingNote);
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

    /**
     * Applies Android theme colors to the editor via JS bridge.
     */
    public void applyTheme() {
        if (editorInterface != null) {
            editorInterface.setThemeColors(
                    new SettingsEditorColors().getThemeColors(getContext())
            );
        }
    }

    void showEditor() {
        loader.animate().alpha(0f).setDuration(300).withEndAction(() -> loader.setVisibility(View.GONE)).start();
        webView.setAlpha(0f);
        webView.setVisibility(View.VISIBLE);
        webView.animate().alpha(1f).setDuration(400).setStartDelay(100).start();
    }

    /**
     * Toggles read-only mode inside Editor.js (title and blocks become non-editable).
     */
    public void actionRead() {
        if (!editorIsReady) return;
        editorInterface.toggleReadMode();
    }

    public void deleteBlock(String blockId, String fileUrl) {
        if (!editorIsReady) return;
        editorInterface.deleteAttachmentBlockRequest(blockId, fileUrl);
    }

    /**
     * Loads a note into the editor.
     * If the editor is not ready yet, the note is stored temporarily.
     */
    public void load(Note mNote) {
        if (mNote == null) {
            pendingNote = null;
            return;
        }

        if (editorIsReady) {
            editorInterface.loadNoteToEditor(mNote);
        } else {
            pendingNote = mNote;
        }
    }

    /**
     * Handles result from WebView file chooser, including validation
     * (size limit, free space), before passing the file to JS.
     */
    public void onFileChooserResult(int resultCode, Intent data) {
        if (fileCallback == null) return;

        Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);

        if (result != null && result.length > 0) {

            Uri uri = result[0];

            AttachmentStorage.AttachmentValidationResult validation =
                    AttachmentStorage.validateBeforeAttach(getContext(), uri);

            if (!validation.ok) {
                Toast.makeText(getContext(), validation.error, Toast.LENGTH_SHORT).show();
                fileCallback.onReceiveValue(null);
                fileCallback = null;
                return;
            }
        }

        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    /**
     * Fully and safely destroys the WebView instance to prevent memory leaks.
     * Must be called from Activity/Fragment onDestroy().
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

        editorInterface = null;

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    /**
     * Soft refresh animation:
     * - shows loader for 500ms
     * - fades out and fades in WebView
     * Does NOT reload HTML or reset scroll.
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
                .withEndAction(() -> {

                    handler.postDelayed(() -> {

                        // Hide loader
                        loader.animate()
                                .alpha(0f)
                                .setDuration(200)
                                .withEndAction(() -> loader.setVisibility(View.GONE))
                                .start();

                        // Show editor again
                        webView.animate()
                                .alpha(1f)
                                .setDuration(200)
                                .start();

                    }, 500); // loader visible ~0.5 sec

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

