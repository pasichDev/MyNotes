package com.pasich.mynotes.utils.noteEditor;


import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.utils.noteEditor.attach.AttachmentSecureStorage;
import com.pasich.mynotes.utils.noteEditor.attach.AttachmentsConst;
import com.pasich.mynotes.utils.noteEditor.models.EditorAttachment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Map;

/**
 * JavaScript Interface for Editor.js communication
 */
public record EditorJSInterface(EditorListener listener, WebView webView, Context appContext) {
    public static final String nameInterface = "Android";
    private static final String TAG = "EditorJSInterface";

    public EditorJSInterface(EditorListener listener, WebView webView, Context appContext) {
        this.listener = listener;
        this.webView = webView;
        this.appContext = appContext.getApplicationContext();
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onEditorReady() {
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
                json.put("valueJson", new JSONArray(note.getValueJson()));
            } else {
                // зовсім порожня нотатка
                json.put("valueJson", new JSONArray());
            }

            json.put("plainTextFallback", isPlainTextFallback);

            String jsCommand = "loadNote(JSON.parse(" + JSONObject.quote(json.toString()) + "));";

            webView.post(() -> webView.evaluateJavascript(jsCommand, null));
        } catch (Exception e) {
            Log.e(TAG, "Failed to load note: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public String uploadFile(String base64, String originalName) {

        int noteId = listener.getNoteId();

        AttachmentSecureStorage storage = new AttachmentSecureStorage();

        // strip base64 header
        int idx = base64.indexOf(",");
        if (idx != -1) {
            base64 = base64.substring(idx + 1);
        }

        byte[] raw = Base64.decode(base64, Base64.DEFAULT);
        if (raw == null || raw.length == 0) return "";

        // save encrypted file
        File saved = storage.saveEncrypted(appContext, noteId, originalName, raw);
        if (saved == null) return "";

        // get file name
        String folder = "note_" + noteId;
        String fileName = saved.getName();

        // 🔥 Створюємо URI у форматі:
        // scheme://attachments_secure/note_12/file.ext.enc
        return new Uri.Builder()
                .scheme("scheme")
                .authority(AttachmentsConst.ATTACH_DIR)
                .appendPath(folder)
                .appendPath(fileName)
                .build()
                .toString();
    }


    @SuppressWarnings("unused")
    @JavascriptInterface
    public void openAttachment(String json) {
        listener.openFile(EditorAttachment.parseSingleAttachment(json));
    }


    public interface EditorListener {
        void onEditorReady();

        void onContentChanged(String jsonData);

        void onTitleChanged(String tile);

        void onError(String error);

        void openFile(EditorAttachment attachment);

        int getNoteId();
    }


}
