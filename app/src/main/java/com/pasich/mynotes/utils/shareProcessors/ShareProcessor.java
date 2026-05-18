package com.pasich.mynotes.utils.shareProcessors;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

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

    public static void readFileAsync(ContentResolver resolver, Uri uri, FileReadCallback callback) {
        new Thread(
                        () -> {
                            try {
                                StringBuilder sb = new StringBuilder();
                                try (BufferedReader reader =
                                        new BufferedReader(
                                                new InputStreamReader(
                                                        resolver.openInputStream(uri)))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        sb.append(line).append("\n");

                                        if (sb.length() > MAX_SHARE_SIZE) break;
                                    }
                                }
                                callback.onSuccess(sb.toString());
                            } catch (IOException e) {
                                callback.onError(e);
                            }
                        })
                .start();
    }

    public static boolean isTooLarge(String text) {
        if (text == null) return false;
        return text.getBytes().length > MAX_SHARE_SIZE;
    }

    public interface FileReadCallback {
        void onSuccess(String content);

        void onError(IOException error);
    }
}
