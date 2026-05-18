package com.pasich.mynotes.extendedEditor.utils;

import static com.pasich.mynotes.extendedEditor.utils.EditorJsScheme.EDITORJS_SCHEME;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.pasich.mynotes.extendedEditor.attach.AttachmentStorage;
import com.pasich.mynotes.extendedEditor.models.EditorAttachment;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;

/** WebViewClient that intercepts editorjs:// requests to serve local attachment files. */
public class EditorAttachmentsWebViewClient extends WebViewClient {

    private static final String TAG = "EditorAttachmentsClient";
    private final Context context;

    public EditorAttachmentsWebViewClient(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();
        return EDITORJS_SCHEME.equals(uri.getScheme());
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();

        if (!EDITORJS_SCHEME.equals(uri.getScheme()))
            return super.shouldInterceptRequest(view, request);

        try {
            List<String> segments = uri.getPathSegments();
            if (segments.size() < 2) return super.shouldInterceptRequest(view, request);

            String noteStr = segments.get(0); // note_146
            String fileName = segments.get(1); // abc.jpg

            int noteId = Integer.parseInt(noteStr.replace("note_", ""));

            String internalUrl =
                    new Uri.Builder()
                            .scheme(EDITORJS_SCHEME)
                            .authority("attachments")
                            .appendPath("note_" + noteId)
                            .appendPath(fileName)
                            .build()
                            .toString();

            EditorAttachment att = new EditorAttachment(internalUrl);

            File file = AttachmentStorage.read(context, att);
            if (file == null || !file.exists()) {
                return super.shouldInterceptRequest(view, request);
            }

            String mime = getMimeFromName(fileName);

            return new WebResourceResponse(mime, null, new FileInputStream(file));

        } catch (Exception e) {
            Log.e(TAG, "intercept error " + e.getMessage());
            return super.shouldInterceptRequest(view, request);
        }
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        Log.e(TAG, "onReceivedError() " + error);
    }

    private String getMimeFromName(String name) {
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "mp4" -> "video/mp4";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }
}
