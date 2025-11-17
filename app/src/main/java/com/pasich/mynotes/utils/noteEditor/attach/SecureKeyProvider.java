package com.pasich.mynotes.utils.noteEditor.attach;

import android.util.Log;
import com.pasich.mynotes.BuildConfig;

public class SecureKeyProvider {

    private static final String TAG = "SecureKeyProvider";

    public static byte[] getKey() {
        if (BuildConfig.ATTACH_KEY.isEmpty()) {
            Log.e(TAG, "ATTACH_KEY is missing! Attachments will NOT be secure.");
            return null;
        }

        // AES256 needs 32 bytes
        byte[] key = new byte[32];
        byte[] source = BuildConfig.ATTACH_KEY.getBytes();

        for (int i = 0; i < key.length; i++) {
            key[i] = source[i % source.length];
        }
        return key;
    }
}
