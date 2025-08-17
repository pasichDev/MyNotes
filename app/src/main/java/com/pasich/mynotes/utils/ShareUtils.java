package com.pasich.mynotes.utils;


import android.app.Activity;
import android.content.Intent;

import com.pasich.mynotes.R;

public class ShareUtils {

    public static void shareNotes(Activity activity, String textShare) {
        if (!(textShare.length() == 0)) {
            activity.startActivity(Intent.createChooser(new Intent("android.intent.action.SEND").setType("plain/text").putExtra("android.intent.extra.TEXT", textShare), activity.getString(R.string.share)));

        }
    }


}
