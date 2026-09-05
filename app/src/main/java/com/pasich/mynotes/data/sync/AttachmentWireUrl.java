package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.UUID;

/**
 * The device-independent form of an attachment reference inside a note's editor blocks.
 *
 * <p>A block on the device that wrote it names {@code editorjs://attachments/note_7/photo.png}: a
 * Room row id and a file name that exist nowhere else. Restoring on another device necessarily
 * rewrites it, so the same note hashed differently on every device that held it, and the merge
 * reported a conflict against itself on every sync. On the wire a block therefore names the
 * attachment's logical id, which is the one identity every device agrees on; each store maps it to
 * its own file on the way in and back on the way out.
 *
 * <p>The scheme is deliberately one nothing else parses: if a wire reference ever leaked into a
 * stored note it would render as a missing file rather than be mistaken for a local path.
 */
final class AttachmentWireUrl {

    private static final String PREFIX = "mynotes-sync://attachment/";

    private AttachmentWireUrl() {}

    @NonNull
    static String forLogicalId(@NonNull String logicalId) {
        return PREFIX + logicalId;
    }

    /** The logical id a wire reference names, or {@code null} for any other URL. */
    @Nullable
    static String logicalIdOf(@Nullable String url) {
        if (url == null || !url.startsWith(PREFIX)) {
            return null;
        }
        String id = url.substring(PREFIX.length());
        try {
            return UUID.fromString(id).toString().equals(id) ? id : null;
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }
}
