package com.pasich.mynotes.utils.navigation;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.widget.Toast;

import com.pasich.mynotes.R;

public class GoogleTranslateHelper {

    public static final String TRANSLATE_PACKAGE = "com.google.android.apps.translate";

    public static void startTranslation(Activity activity, String text) {
        try {
            Intent translateIntent = new Intent(Intent.ACTION_PROCESS_TEXT);
            translateIntent.setType("text/plain");
            translateIntent.putExtra(Intent.EXTRA_PROCESS_TEXT, text);
            translateIntent.putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
            translateIntent.setPackage(TRANSLATE_PACKAGE);

            // Set flags so we don’t break back stack
            translateIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            translateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            activity.startActivity(translateIntent);

        } catch (ActivityNotFoundException e) {
            // If Google Translate not installed
            Toast.makeText(activity, R.string.notInstaledAppTranslate, Toast.LENGTH_SHORT).show();

        }
    }
}
