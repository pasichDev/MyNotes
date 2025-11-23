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
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.view.ViewCompat;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;
import com.pasich.mynotes.extendedEditor.models.EditorAttachment;
import com.pasich.mynotes.extendedEditor.utils.EditorJSInterface;
import com.pasich.mynotes.extendedEditor.utils.SettingsEditorColors;

import java.util.Locale;

public class NoteEditorView extends FrameLayout {

    public static final int FILE_CHOOSER_REQUEST = 2025;
    private WebView webView;
    private View loader;
    private EditorJSInterface editorInterface;
    private Handler handler;
    private Note note;
    private Note pendingNote;
    private boolean editorIsReady = false;
    private boolean htmlLoaded = false;
    private OnTitleChangedListener titleListener;
    private OnContentChangedListener contentListener;
    private OnAttachmentClickListener attachmentListener;
    private OnFileChooserListener fileChooserListener;
    private ValueCallback<Uri[]> fileCallback;


    public NoteEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }


    private void init(Context context) {
        inflate(context, R.layout.view_note_editor, this);

        webView = findViewById(R.id.editorWebView);
        loader = findViewById(R.id.editorLoader);
        handler = new Handler(Looper.getMainLooper());
        ViewCompat.setNestedScrollingEnabled(webView, false);

        setupWebView();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!htmlLoaded) {
            htmlLoaded = true;


            webView.post(this::loadEditorHtml);
        }
    }


    private void loadEditorHtml() {
        webView.loadUrl(
                "file:///android_asset/editor/note_editor.html?locale=" + Locale.getDefault().getLanguage()
        );
    }


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

        editorInterface = new EditorJSInterface(new EditorJSInterface.EditorListener() {

            @Override
            public void onEditorReady() {
                handler.post(() -> {
                    editorIsReady = true;
                    applyTheme();

                    if (pendingNote != null) {
                        editorInterface.loadNoteToEditor(pendingNote);
                        pendingNote = null;
                    }

                    handler.postDelayed(NoteEditorView.this::showEditor, 120);
                });
            }


            @Override
            public void onTitleChanged(String title) {
                handler.post(() -> {
                    if (titleListener != null) titleListener.onTitleChanged(title);
                });
            }


            @Override
            public void onContentChanged(String json) {
                handler.post(() -> {
                    if (contentListener != null) contentListener.onContentChanged(json);
                });
            }

            @Override
            public void openFile(EditorAttachment attachment) {
                handler.post(() -> {
                    if (attachmentListener != null)
                        attachmentListener.onAttachmentClick(attachment);
                });
            }

            @Override
            public int getNoteId() {
                return (note != null && note.getId() > 0) ? note.getId() : 0;
            }

            @Override
            public void onError(String error) {
                handler.post(() -> Log.e("NoteActivityBeta", "Editor error: " + error));
            }

        }, webView, getContext());

        webView.setWebViewClient(new WebViewClient() {
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


        webView.addJavascriptInterface(editorInterface, EditorJSInterface.nameInterface);
    }

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


    public void load(Note mNote) {

        if (mNote == null) {
            pendingNote = null;
            return;
        }

        note = mNote;

        if (editorIsReady) {
            editorInterface.loadNoteToEditor(mNote);
        } else {
            pendingNote = mNote;
        }
    }


    public void setOnTitleChangedListener(OnTitleChangedListener l) {
        this.titleListener = l;
    }

    public void setOnContentChangedListener(OnContentChangedListener l) {
        this.contentListener = l;
    }

    public void setOnAttachmentClickListener(OnAttachmentClickListener l) {
        this.attachmentListener = l;
    }

    public void actionRead() {
        if (!editorIsReady) return;
        handler.post(() -> webView.evaluateJavascript("toggleReadModeFromAndroid();", null));
    }

    public void setOnFileChooserListener(OnFileChooserListener l) {
        this.fileChooserListener = l;
    }

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

    public void release() {
        try {
            if (webView != null) {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.clearHistory();
                webView.clearCache(false);
                webView.removeJavascriptInterface(EditorJSInterface.nameInterface);
                webView.setWebChromeClient(null);
                webView.setWebViewClient(null);
                webView.destroy();
                webView = null;
            }
        } catch (Exception e) {
            Log.e("NoteEditorView", "WebView cleanup error: " + e.getMessage());
        }

        editorInterface = null;

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
    }

    public interface OnTitleChangedListener {
        void onTitleChanged(String t);
    }


    public interface OnContentChangedListener {
        void onContentChanged(String json);
    }

    public interface OnAttachmentClickListener {
        void onAttachmentClick(EditorAttachment attachment);
    }

    public interface OnFileChooserListener {
        void onOpenFileChooser(Intent intent, int requestCode);
    }

}
