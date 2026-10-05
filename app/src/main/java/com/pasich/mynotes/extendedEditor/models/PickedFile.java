package com.pasich.mynotes.extendedEditor.models;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** A file the system picker returned: its content URI plus the name and size it reported. */
public final class PickedFile {

    @NonNull public final Uri uri;
    @Nullable public final String name;
    public final long size;

    public PickedFile(@NonNull Uri uri, @Nullable String name, long size) {
        this.uri = uri;
        this.name = name;
        this.size = size;
    }

    /**
     * Whether this is the file the page is uploading. The page's File carries the display name and
     * size the provider reported, so either one identifying it is enough.
     */
    public boolean matches(@Nullable String pageName, long pageSize) {
        if (name != null && !name.isEmpty() && name.equals(pageName)) return true;
        return size > 0 && size == pageSize;
    }
}
