package com.pasich.mynotes.utils.editor;

import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.pasich.mynotes.data.model.Note;

import org.json.JSONObject;

import java.util.Map;

/**
 * JavaScript Interface for Editor.js communication
 */
public class EditorJSInterface {
    public static final String nameInterface = "Android";
    private static final String TAG = "EditorJSInterface";
    private final EditorListener listener;
    private final WebView webView;

    public EditorJSInterface(EditorListener listener, WebView webView) {
        this.listener = listener;
        this.webView = webView;
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onEditorReady() {
        Log.d(TAG, "Editor.js is ready");
        if (listener != null) listener.onEditorReady();
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onContentChanged(String jsonData) {
        if (listener != null) listener.onContentChanged(jsonData);
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onTitleChanged(String title) {
        webView.post(() -> listener.onTitleChanged(title));
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onError(String error) {
        Log.e(TAG, "Editor.js error: " + error);
        if (listener != null) listener.onError(error);
    }

    /**
     * Метод для зміни кольорів редактора з Java
     */
    public void setThemeColors(Map<String, String> colors) {
        if (webView == null || colors == null) return;

        try {
            JSONObject json = new JSONObject(colors);
            final String jsCommand = "setThemeColors(" + json + ");";

            webView.post(() -> webView.evaluateJavascript(jsCommand, null));
        } catch (Exception e) {
            Log.e(TAG, "Failed to set theme colors: " + e.getMessage());
        }
    }

    public void loadNoteToEditor(Note note) {
        if (webView == null || note == null) return;
        Log.d(TAG, "JS Command: " + note.hasRichContent());
        try {
            JSONObject json = new JSONObject();
            json.put("title", note.getTitle());

            boolean isPlainTextFallback = false;

            if (!note.hasRichContent() && (note.getValueJson() == null || note.getValueJson().isEmpty())) {
                // стара нотатка → передаємо plainText
                isPlainTextFallback = true;
                json.put("plainText", note.getValue() != null ? note.getValue() : "");
            } else if (note.getValueJson() != null && !note.getValueJson().isEmpty()) {
                // оптимізована нотатка → передаємо valueJson
                json.put("valueJson", new org.json.JSONArray(note.getValueJson()));
            } else {
                // зовсім порожня нотатка
                json.put("valueJson", new org.json.JSONArray());
            }

            json.put("plainTextFallback", isPlainTextFallback);


            final String jsCommand = "loadNote(" + json + ");";
            Log.d(TAG, "JS Command: " + jsCommand);
            webView.post(() -> webView.evaluateJavascript(jsCommand, null));
        } catch (Exception e) {
            Log.e(TAG, "Failed to load note: " + e.getMessage(), e);
        }
    }


    public interface EditorListener {
        void onEditorReady();

        void onContentChanged(String jsonData);

        void onTitleChanged(String tile);

        void onError(String error);
    }


}
