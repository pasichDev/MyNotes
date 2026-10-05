package com.pasich.mynotes.extendedEditor.utils;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;
import com.pasich.mynotes.extendedEditor.attach.RecentAttachmentUploads;
import com.pasich.mynotes.extendedEditor.models.EditorAttachment;
import com.pasich.mynotes.extendedEditor.models.PickedFile;
import com.pasich.mynotes.extendedEditor.models.SettingsEditorJsBridge;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * JavaScript bridge used to communicate between Android and Editor.js inside the WebView.
 *
 * <p>Handles incoming JS callbacks, forwards events to the EditorListener, and exposes selected
 * Android-side functionality (file upload, theme sync, etc.) to JavaScript
 * through @JavascriptInterface methods.
 */
public class EditorJSInterface {

    public static final String nameInterface = "Android";

    /** Upload kind for the image tool; anything else is stored as a plain attachment. */
    public static final String KIND_IMAGE = "image";

    public static final String KIND_FILE = "file";
    private static final String TAG = "EditorJSInterface";

    private final EditorListener listener;
    private final WebView webView;
    private final Context appContext;
    private final SettingsEditorJsBridge settings;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // One file at a time, in the order they were picked; never on the JS or UI thread.
    private final ExecutorService uploads = Executors.newSingleThreadExecutor();
    private final List<PickedFile> pickedFiles = new ArrayList<>();
    private volatile boolean released = false;

    public EditorJSInterface(
            EditorListener listener,
            WebView webView,
            Context appContext,
            SettingsEditorJsBridge settings) {

        this.listener = listener;
        this.webView = webView;
        this.appContext = appContext.getApplicationContext();
        this.settings = settings;
    }

    /**
     * Called from JavaScript when Editor.js has fully initialized. Forwards the event to the
     * EditorListener.
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
     * Answer to {@link #requestFlush()}: the document as it is right now, sent without waiting for
     * the editor's change batching.
     */
    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onContentFlushed(String jsonData) {
        if (listener != null) listener.onContentFlushed(jsonData);
    }

    /**
     * Reports which block is at the top of the viewport once scrolling settles, so the reading
     * position can be restored after a rotation.
     *
     * @param blockIndex index of the first visible block, or -1 while the title is on screen.
     * @param offsetPx that block's distance from the top of the viewport, in CSS pixels.
     */
    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onViewportAnchor(int blockIndex, int offsetPx) {
        if (listener != null) listener.onViewportAnchor(blockIndex, offsetPx);
    }

    /** The note handed over by loadNoteToEditor has been rendered. */
    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onNoteRendered() {
        if (listener != null) listener.onNoteRendered();
    }

    /** Asks the editor to send its document now; it answers through onContentFlushed. */
    public void requestFlush() {
        if (webView == null) return;
        webView.post(
                () -> webView.evaluateJavascript("window.flushContent && flushContent();", null));
    }

    /**
     * Called from JS whenever the title field inside the editor changes. Executed on the main
     * thread to safely update UI listeners.
     */
    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onTitleChanged(String title) {
        webView.post(() -> listener.onTitleChanged(title));
    }

    /** Receives error messages thrown by the JS editor and reports them to listener. */
    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onError(String error) {
        if (listener != null) listener.onError(error);
    }

    /**
     * Sends a map of theme color values to the WebView, applying the current Android theme inside
     * Editor.js through custom CSS variables.
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
     * Loads a note into Editor.js by passing a JSON structure via evaluateJavascript. Handles
     * legacy notes (plain text), optimized rich notes, or empty notes.
     *
     * @param note The note model which will be rendered in the editor.
     */
    public void loadNoteToEditor(Note note) {
        loadNoteToEditor(note, -1, 0);
    }

    /**
     * Loads a note and, once it is rendered, scrolls block {@code anchorIndex} to {@code
     * anchorOffset} CSS pixels from the top. A negative index keeps the top of the note.
     */
    public void loadNoteToEditor(Note note, int anchorIndex, int anchorOffset) {
        if (webView == null || note == null) return;
        try {
            JSONObject json = new JSONObject();
            json.put("title", note.getTitle());

            boolean isPlainTextFallback = false;

            if (!note.isAttachments()
                    && (note.getValueJson() == null || note.getValueJson().isEmpty())) {
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
            if (anchorIndex >= 0) {
                JSONObject anchor = new JSONObject();
                anchor.put("index", anchorIndex);
                anchor.put("offset", anchorOffset);
                json.put("anchor", anchor);
            }
            String jsCommand = "loadNote(JSON.parse(" + JSONObject.quote(json.toString()) + "));";
            webView.post(() -> webView.evaluateJavascript(jsCommand, null));
        } catch (Exception e) {
            Log.e(TAG, "Failed to load note: " + e.getMessage(), e);
        }
    }

    /**
     * Files the system picker just returned, offered to the page's next upload request. The page
     * only sees a File; Android keeps the content URI and reads it itself, so the bytes never cross
     * the bridge.
     */
    public void offerPickedFiles(List<PickedFile> files) {
        synchronized (pickedFiles) {
            pickedFiles.clear();
            pickedFiles.addAll(files);
        }
    }

    /**
     * Starts storing a file the picker returned, off the JS and UI threads.
     *
     * @return true when the file was found and the upload started; the result then arrives through
     *     {@code window.__onUploadFinished(requestId, result)}. False when the page has to send the
     *     bytes itself ({@link #uploadBase64Async}).
     */
    @SuppressWarnings("unused")
    @JavascriptInterface
    public boolean requestPickedUpload(String requestId, String kind, String name, double size) {
        PickedFile match = takePickedFile(name, (long) size);
        if (match == null || released) return false;
        uploads.execute(
                () -> {
                    byte[] raw = AttachmentStorage.readUri(appContext, match.uri);
                    deliverUpload(requestId, raw == null ? null : store(raw, match.name, kind));
                });
        return true;
    }

    /**
     * Stores bytes the page sent (a pasted or dropped file) in the background and answers through
     * {@code window.__onUploadFinished(requestId, result)}. Returns at once, so the page is never
     * blocked for the length of the save.
     */
    @SuppressWarnings("unused")
    @JavascriptInterface
    public boolean uploadBase64Async(String requestId, String kind, String base64, String name) {
        if (released || base64 == null) return false;
        uploads.execute(
                () -> {
                    String payload = base64;
                    int comma = payload.indexOf(',');
                    if (comma != -1) payload = payload.substring(comma + 1);
                    byte[] raw;
                    try {
                        raw = Base64.decode(payload, Base64.DEFAULT);
                    } catch (IllegalArgumentException e) {
                        raw = null;
                    }
                    deliverUpload(requestId, raw == null ? null : store(raw, name, kind));
                });
        return true;
    }

    /**
     * Stores a file the picker returned to a page that no longer exists (the screen was recreated
     * while the picker was open) and inserts its block at {@code index}.
     *
     * @param onFailed run on the main thread when the file could not be stored.
     */
    public void uploadAndInsert(String kind, PickedFile file, int index, Runnable onFailed) {
        if (released) return;
        uploads.execute(
                () -> {
                    byte[] raw = AttachmentStorage.readUri(appContext, file.uri);
                    JSONObject result = raw == null ? null : store(raw, file.name, kind);
                    mainHandler.post(
                            () -> {
                                if (released || webView == null) return;
                                JSONObject stored =
                                        result == null ? null : result.optJSONObject("file");
                                if (stored == null) {
                                    onFailed.run();
                                    return;
                                }
                                webView.evaluateJavascript(
                                        "window.insertUploadedBlockFromAndroid && "
                                                + "insertUploadedBlockFromAndroid("
                                                + JSONObject.quote(kind)
                                                + ","
                                                + stored
                                                + ","
                                                + index
                                                + ");",
                                        null);
                            });
                });
    }

    /** Stops accepting uploads; called when the WebView is destroyed. */
    public void release() {
        released = true;
        uploads.shutdownNow();
    }

    private PickedFile takePickedFile(String name, long size) {
        synchronized (pickedFiles) {
            for (int i = 0; i < pickedFiles.size(); i++) {
                PickedFile candidate = pickedFiles.get(i);
                if (candidate.matches(name, size)) {
                    return pickedFiles.remove(i);
                }
            }
            return null;
        }
    }

    /**
     * Saves raw bytes into the note's attachment folder and describes the stored file in the
     * Editor.js upload response format. Runs on the upload thread.
     */
    private JSONObject store(byte[] raw, String originalName, String kind) {
        if (raw == null || raw.length == 0) return null;
        String name =
                originalName == null || originalName.isEmpty()
                        ? "file"
                        : originalName.replace("'", "_").replace(" ", "_");
        int noteId = listener.getNoteId();
        File saved =
                AttachmentStorage.save(
                        appContext, noteId, name, raw, settings.isExtraOptimizeEnabled());
        if (saved == null) return null;
        RecentAttachmentUploads.register(noteId, saved.getName());
        try {
            JSONObject file = new JSONObject();
            file.put("url", AttachmentStorage.urlFor(noteId, saved.getName()));
            if (KIND_IMAGE.equals(kind)) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(saved.getAbsolutePath(), bounds);
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    file.put("width", bounds.outWidth);
                    file.put("height", bounds.outHeight);
                }
            } else {
                int dot = name.lastIndexOf('.');
                file.put("name", name);
                file.put("size", saved.length());
                file.put("extension", dot == -1 ? "" : name.substring(dot + 1));
            }
            JSONObject root = new JSONObject();
            root.put("success", 1);
            root.put("file", file);
            return root;
        } catch (Exception e) {
            Log.e(TAG, "Failed to describe the stored file", e);
            return null;
        }
    }

    private void deliverUpload(String requestId, JSONObject result) {
        String script =
                "window.__onUploadFinished && __onUploadFinished("
                        + JSONObject.quote(requestId)
                        + ","
                        + (result == null ? "null" : result.toString())
                        + ");";
        mainHandler.post(
                () -> {
                    if (released || webView == null) return;
                    webView.evaluateJavascript(script, null);
                });
    }

    /**
     * Called when JS requests to open an attachment preview. Parses attachment JSON and forwards it
     * to the listener (Activity).
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
     *     <p>Deletes the physical attachment file if the URL is not null and shows a toast with the
     *     result.
     */
    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onAttachmentBlockDeletedResponse(String blockId, String fileUrl) {
        if (fileUrl != null) {
            boolean removed = AttachmentStorage.delete(appContext, new EditorAttachment(fileUrl));
            if (!removed) {
                Toast.makeText(
                                appContext,
                                appContext.getString(R.string.deleteAttachmentsErrorExits),
                                Toast.LENGTH_SHORT)
                        .show();
                return;
            }
        }
        Toast.makeText(
                        appContext,
                        appContext.getString(R.string.deleteAttachmentsSuccess),
                        Toast.LENGTH_SHORT)
                .show();
    }

    /** Toggles read-only mode inside Editor.js (title and blocks become non-editable). */
    public void toggleReadMode() {
        webView.post(() -> webView.evaluateJavascript("toggleReadModeFromAndroid();", null));
    }

    /**
     * Requests JavaScript to delete a specific Editor.js block.
     *
     * @param blockId The ID of the block to remove.
     * @param fileUrl URL of the attached file. If null → Android should not delete the physical
     *     file.
     */
    public void deleteAttachmentBlockRequest(String blockId, String fileUrl) {
        // If fileUrl == null → JS should receive “null” instead of “null string”
        String jsUrl = (fileUrl == null) ? "null" : "'" + fileUrl.replace("'", "\\'") + "'";

        webView.evaluateJavascript(
                "deleteAttachmentBlockFromAndroid('" + blockId + "', " + jsUrl + ");", null);
    }

    /**
     * Called from the JS ImageTool extension when user long-presses an image block.
     *
     * <p>Receives the unique blockId generated by Editor.js and delegates
     *
     * @param blockId Editor.js block unique identifier
     */
    @SuppressWarnings("unused")
    @JavascriptInterface
    public void onImageBlockClick(String blockId) {
        listener.openPhoto(blockId);
    }

    public interface EditorListener {
        void onEditorReady();

        void onContentChanged(String jsonData);

        void onContentFlushed(String jsonData);

        void onViewportAnchor(int blockIndex, int offsetPx);

        void onNoteRendered();

        void onTitleChanged(String tile);

        void onError(String error);

        void openFile(EditorAttachment attachment);

        void openPhoto(String blockId);

        int getNoteId();
    }
}
