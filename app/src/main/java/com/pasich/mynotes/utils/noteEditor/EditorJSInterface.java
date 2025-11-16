package com.pasich.mynotes.utils.noteEditor;

import static com.preference.provider.PreferenceProvider.context;

import android.content.Context;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.pasich.mynotes.data.model.Note;
import com.preference.provider.PreferenceProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * JavaScript Interface for Editor.js communication
 */
public class EditorJSInterface {
    public static final String nameInterface = "Android";
    private static final String TAG = "EditorJSInterface";
    private final EditorListener listener;
    private final WebView webView;
    private final Context appContext;
    public EditorJSInterface(EditorListener listener, WebView webView, Context context) {
        this.listener = listener;
        this.webView = webView;
        this.appContext = context.getApplicationContext();
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


            String jsCommand = "loadNote(JSON.parse(" + JSONObject.quote(json.toString()) + "));";

            Log.d(TAG, "JS Command: " + jsCommand);
            webView.post(() -> webView.evaluateJavascript(jsCommand, null));
        } catch (Exception e) {
            Log.e(TAG, "Failed to load note: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public String uploadFile(String base64, String fileName) {
        Log.d(TAG, "uploadFile(): start");

        if (base64 == null || base64.isEmpty()) {
            Log.e(TAG, "uploadFile(): base64 is NULL or empty");
            return "";
        }

        if (fileName == null || fileName.trim().isEmpty()) {
            Log.w(TAG, "uploadFile(): fileName is empty, generating fallback name");
            fileName = "file_" + System.currentTimeMillis();
        }

        try {
            // 1) Очистити префікс
            int commaIndex = base64.indexOf(",");
            if (commaIndex != -1) {
                base64 = base64.substring(commaIndex + 1);
            } else {
                Log.w(TAG, "uploadFile(): no comma found in base64, using original string");
            }

            // 2) Декодуємо
            byte[] data;
            try {
                data = Base64.decode(base64, Base64.DEFAULT);
            } catch (Exception e) {
                Log.e(TAG, "uploadFile(): FAILED TO DECODE BASE64", e);
                return "";
            }

            if (data.length == 0) {
                Log.e(TAG, "uploadFile(): decoded byte array is EMPTY");
                return "";
            }

            // 3) Створюємо директорію attachments
            File dir = new File(appContext.getFilesDir(), "attachments");
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                Log.d(TAG, "uploadFile(): create dir " + created + " → " + dir.getAbsolutePath());
            }

            // 4) Уникаємо колізій імен
            File file = new File(dir, fileName);
            if (file.exists()) {
                String name = fileName;
                String ext = "";
                int dotIndex = fileName.lastIndexOf(".");
                if (dotIndex != -1) {
                    name = fileName.substring(0, dotIndex);
                    ext = fileName.substring(dotIndex);
                }
                file = new File(dir, name + "_" + System.currentTimeMillis() + ext);
                Log.w(TAG, "uploadFile(): filename collision → using " + file.getName());
            }

            // 5) Записуємо файл
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
                fos.flush();
            }

            Log.d(TAG, "uploadFile(): file saved → " + file.getAbsolutePath() +
                    " (" + data.length + " bytes)");

            // 6) Повертаємо URL
            String url = "file://" + file.getAbsolutePath();
            Log.d(TAG, "uploadFile(): return URL " + url);

            return url;

        } catch (Exception e) {
            Log.e(TAG, "uploadFile(): ERROR", e);
            return "";
        }
    }



    public interface EditorListener {
        void onEditorReady();

        void onContentChanged(String jsonData);

        void onTitleChanged(String tile);

        void onError(String error);
    }


}
