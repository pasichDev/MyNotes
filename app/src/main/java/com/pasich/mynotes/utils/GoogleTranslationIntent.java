package com.pasich.mynotes.utils;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.widget.Toast;

import com.pasich.mynotes.R;

public class GoogleTranslationIntent {

   public static String packageTranslator = "com.google.android.apps.translate";

    public void startTranslation(Activity activity, String text) {

        PackageInfo pi = null;
        try {
            pi = activity.getPackageManager().getPackageInfo(packageTranslator, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        if (pi != null) {
            Intent intent = new Intent();
            intent.setType("text/plain");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                intent.setAction(Intent.ACTION_PROCESS_TEXT);
                intent.putExtra(Intent.EXTRA_PROCESS_TEXT, text);
            } else {
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, text);
            }

            for (ResolveInfo resolveInfo : activity.getPackageManager().queryIntentActivities(intent, 0)) {

                if (resolveInfo.activityInfo.packageName.contains(packageTranslator)) {
                    intent.setComponent(new ComponentName(
                            resolveInfo.activityInfo.packageName,
                            resolveInfo.activityInfo.name));
                    activity.startActivity(intent);
                }

            }
        } else {
            Toast.makeText(activity, R.string.notInstaledAppTranslate, Toast.LENGTH_SHORT).show();
        }
    }
}
