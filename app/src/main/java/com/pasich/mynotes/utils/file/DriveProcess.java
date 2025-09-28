package com.pasich.mynotes.utils.file;

import android.net.Uri;

public interface DriveProcess {
    void onSuccess(Uri uri, String nameFile);

    void onError(String error);
}
