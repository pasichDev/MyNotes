package com.pasich.mynotes.utils.shareProcessors;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class ShareProcessor {

    public static final int MAX_SHARE_SIZE = 500_000;

    public static String extractProcessText(Intent intent) {
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        return text != null ? text.toString() : "";
    }

    public static boolean isShareIntent(Intent intent) {
        return intent.getType() != null && "text/plain".equals(intent.getType());
    }

    public static String extractSharedText(Intent intent) {
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        return text != null ? text : "";
    }

    /**
     * Whether a file another app asks to open as a note may be read. The handler is exported, so
     * any app can send it any URI: only another app's content is read. A {@code file://} URI or one
     * of this app's own providers would copy the app's private files into a note the sender could
     * then get back.
     *
     * @param packageName this app's package name; its providers' authorities start with it.
     */
    public static boolean isReadableSharedUri(@Nullable Uri uri, String packageName) {
        if (uri == null || !ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String authority = uri.getAuthority();
        if (authority == null || authority.isEmpty()) return false;
        String own = packageName.toLowerCase(Locale.ROOT);
        // An authority may list several, separated by semicolons.
        for (String one : authority.toLowerCase(Locale.ROOT).split(";")) {
            String name = one.contains("@") ? one.substring(one.lastIndexOf('@') + 1) : one;
            if (name.equals(own) || name.startsWith(own + ".")) return false;
        }
        return true;
    }

    /**
     * Reads the text at {@code uri} on a background thread. Any failure, a provider that refuses or
     * returns nothing included, reaches {@code callback.onError} instead of crashing the thread.
     */
    public static void readFileAsync(ContentResolver resolver, Uri uri, FileReadCallback callback) {
        new Thread(
                        () -> {
                            String text;
                            try {
                                text = readText(resolver, uri);
                            } catch (Exception e) {
                                callback.onError(e);
                                return;
                            }
                            callback.onSuccess(text);
                        })
                .start();
    }

    private static String readText(ContentResolver resolver, Uri uri) throws IOException {
        InputStream in = resolver.openInputStream(uri);
        if (in == null) throw new IOException("No content for " + uri);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");

                if (sb.length() > MAX_SHARE_SIZE) break;
            }
        }
        return sb.toString();
    }

    public static boolean isTooLarge(String text) {
        if (text == null) return false;
        return text.getBytes().length > MAX_SHARE_SIZE;
    }

    public interface FileReadCallback {
        void onSuccess(String content);

        void onError(Exception error);
    }
}
