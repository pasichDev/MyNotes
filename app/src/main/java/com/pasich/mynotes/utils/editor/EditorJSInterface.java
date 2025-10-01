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
    private static final String TAG = "EditorJSInterface";
    public static final String nameInterface = "Android";

    private final EditorListener listener;
    private final WebView webView;

    public interface EditorListener {
        void onEditorReady();

        void onContentChanged(String jsonData);

        void onTitleChanged(String tile);

        void onError(String error);
    }

    public EditorJSInterface(EditorListener listener, WebView webView) {
        this.listener = listener;
        this.webView = webView;
    }

    @JavascriptInterface
    public void onEditorReady() {
        Log.d(TAG, "Editor.js is ready");
        if (listener != null) listener.onEditorReady();
    }

    @JavascriptInterface
    public void onContentChanged(String jsonData) {
        Log.d(TAG, "jsonData: " + jsonData);
        if (listener != null) listener.onContentChanged(jsonData);
    }

    @JavascriptInterface
    public void onTitleChanged(String title) {
        Log.d(TAG, "tile: " + title);
        webView.post(() -> listener.onTitleChanged(title));
    }

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

        try {
            JSONObject json = new JSONObject();
            json.put("title", note.getTitle());

            // Серіалізуємо valueJson у JSONArray, а не в рядок
            if (note.getValueJson() != null && !note.getValueJson().isEmpty()) {
                json.put("valueJson", new org.json.JSONArray(note.getValueJson()));
            } else {
                // fallback на порожній блок
                json.put("valueJson", new org.json.JSONArray());
            }

            final String jsCommand = "loadNote(" + json + ");";
            Log.d(TAG, "JS Command: " + jsCommand);
            webView.post(() -> webView.evaluateJavascript(jsCommand, null));
        } catch (Exception e) {
            Log.e(TAG, "Failed to load note: " + e.getMessage(), e);
        }
    }


}
