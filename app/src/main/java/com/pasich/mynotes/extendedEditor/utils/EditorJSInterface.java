package com.pasich.mynotes.extendedEditor.utils;


import static com.pasich.mynotes.extendedEditor.utils.EditorJsScheme.EDITORJS_SCHEME;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;
import com.pasich.mynotes.extendedEditor.models.EditorAttachment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Map;

/**
 * JavaScript bridge used to communicate between Android and Editor.js inside the WebView.
 * <p>
 * Handles incoming JS callbacks, forwards events to the EditorListener,
 * and exposes selected Android-side functionality (file upload, theme sync, etc.)
 * to JavaScript through @JavascriptInterface methods.
 */


public record EditorJSInterface(EditorListener listener, WebView webView, Context appContext) {
    public static final String nameInterface = "Android";
    private static final String TAG = "EditorJSInterface";

    public EditorJSInterface(EditorListener listener, WebView webView, Context appContext) {
        this.listener = listener;
        this.webView = webView;
        this.appContext = appContext.getApplicationContext();
    }

    /**
     * Called from JavaScript when Editor.js has fully initialized.
     * Forwards the event to the EditorListener.
     */

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onEditorReady() {
        if (listener != null) listener.onEditorReady();
    }

    /**
     * Triggered whenever Editor.js content changes.
     *
     * @param jsonData Serialized array of blocks in JSON format.
     */

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onContentChanged(String jsonData) {
        if (listener != null) listener.onContentChanged(jsonData);

    }

    /**
     * Called from JS whenever the title field inside the editor changes.
     * Executed on the main thread to safely update UI listeners.
     */
    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onTitleChanged(String title) {
        webView.post(() -> listener.onTitleChanged(title));
    }

    /**
     * Receives error messages thrown by the JS editor and reports them to listener.
     */
    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onError(String error) {
        if (listener != null) listener.onError(error);
    }

    /**
     * Sends a map of theme color values to the WebView, applying the current
     * Android theme inside Editor.js through custom CSS variables.
     *
     * @param colors A map of --css-variable -> color value (#RRGGBB)
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

    /**
     * Loads a note into Editor.js by passing a JSON structure via evaluateJavascript.
     * Handles legacy notes (plain text), optimized rich notes, or empty notes.
     *
     * @param note The note model which will be rendered in the editor.
     */

    public void loadNoteToEditor(Note note) {
        if (webView == null || note == null) return;
        try {
            JSONObject json = new JSONObject();
            json.put("title", note.getTitle());

            boolean isPlainTextFallback = false;

            if (!note.isAttachments() && (note.getValueJson() == null || note.getValueJson().isEmpty())) {
                // old note → transfer plainText
                isPlainTextFallback = true;
                json.put("plainText", note.getValue() != null ? note.getValue() : "");
            } else if (note.getValueJson() != null && !note.getValueJson().isEmpty()) {
                // optimized note → passing valueJson
                json.put("valueJson", new JSONArray(note.getValueJson()));
            } else {
                // completely empty note
                json.put("valueJson", new JSONArray());
            }

            json.put("plainTextFallback", isPlainTextFallback);
            String jsCommand = "loadNote(JSON.parse(" + JSONObject.quote(json.toString()) + "));";
            webView.post(() -> webView.evaluateJavascript(jsCommand, null));
        } catch (Exception e) {
            Log.e(TAG, "Failed to load note: " + e.getMessage(), e);
        }
    }

    /**
     * Receives a Base64-encoded file from Editor.js, decodes it,
     * saves it to secure attachment storage and returns a file:// URL
     * that Editor.js stores inside the block metadata.
     *
     * @param base64       Encoded file content sent by JS.
     * @param originalName Name of the file provided by the Editor.js tool.
     * @return A file://attachments/... URL or empty string on error.
     */

    @SuppressWarnings("unused")
    @JavascriptInterface
    public String uploadFile(String base64, String originalName) {
        originalName = originalName.replace("'", "_").replace(" ", "_");

        int noteId = listener.getNoteId();

        // strip header
        int idx = base64.indexOf(",");
        if (idx != -1) base64 = base64.substring(idx + 1);

        byte[] raw = Base64.decode(base64, Base64.DEFAULT);
        if (raw == null || raw.length == 0) return "";

        File saved = AttachmentStorage.save(appContext, noteId, originalName, raw);
        if (saved == null) return "";
        return new Uri.Builder()
                .scheme(EDITORJS_SCHEME)
                .authority("attachments")
                .appendPath("note_" + noteId)
                .appendPath(saved.getName())
                .build()
                .toString();
    }

    /**
     * Accepts a base64 image from Editor.js, stores it in internal storage,
     * and returns an Editor.js-compatible response in the following format:
     * {
     * “success”: 1,
     * “file”: { ‘url’: “editorjs://attachments/...” }
     * }
     * <p>
     * Returns on error:
     * { “success”: 0 }
     * <p>
     * - uploadFile(...) performs the actual save and returns a URL or “”
     */
    @SuppressWarnings("unused")
    @JavascriptInterface
    public String uploadImage(String base64, String originalName) {
        String fileUrl = uploadFile(base64, originalName);

        try {
            JSONObject root = new JSONObject();
            if (fileUrl.isEmpty()) {
                root.put("success", 0);
                return root.toString();
            }

            JSONObject fileObj = new JSONObject();
            fileObj.put("url", fileUrl);

            root.put("success", 1);
            root.put("file", fileObj);

            return root.toString();

        } catch (Exception e) {
            return "{\"success\":0}";
        }
    }


    /**
     * Called when JS requests to open an attachment preview.
     * Parses attachment JSON and forwards it to the listener (Activity).
     */

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void openAttachment(String json) {
        listener.openFile(EditorAttachment.parseSingleAttachment(json));
    }

    /**
     * Called by JavaScript after a block has been successfully deleted inside the editor.
     *
     * @param blockId ID of the deleted block
     * @param fileUrl URL of the file that should be removed (null means file was already missing)
     *                <p>
     *                Deletes the physical attachment file if the URL is not null and shows a toast with the result.
     */

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onAttachmentBlockDeletedResponse(String blockId, String fileUrl) {
        if (fileUrl != null) {
            // Видаляємо файл
            boolean removed = AttachmentStorage.delete(appContext, new EditorAttachment(fileUrl));
            if (!removed) {
                Toast.makeText(appContext, appContext.getString(R.string.deleteAttachmentsErrorExits), Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Toast.makeText(appContext, appContext.getString(R.string.deleteAttachmentsSuccess), Toast.LENGTH_SHORT).show();
    }


    /**
     * Toggles read-only mode inside Editor.js (title and blocks become non-editable).
     */
    public void toggleReadMode() {
        webView.post(() -> webView.evaluateJavascript("toggleReadModeFromAndroid();", null));
    }

    /**
     * Requests JavaScript to delete a specific Editor.js block.
     *
     * @param blockId The ID of the block to remove.
     * @param fileUrl URL of the attached file. If null → Android should not delete the physical file.
     */

    public void deleteAttachmentBlockRequest(String blockId, String fileUrl) {
        // If fileUrl == null → JS should receive “null” instead of “null string”
        String jsUrl = (fileUrl == null)
                ? "null"
                : "'" + fileUrl.replace("'", "\\'") + "'";

        webView.evaluateJavascript("deleteAttachmentBlockFromAndroid('" + blockId + "', " + jsUrl + ");", null);
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
