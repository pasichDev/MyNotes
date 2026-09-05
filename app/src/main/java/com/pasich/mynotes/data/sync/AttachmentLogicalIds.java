package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * How an attachment that the editor never gave an id receives one.
 *
 * <p>Existing editor data predates logical attachment ids, so the store derives one from the note's
 * stable id, the attachment's position, its stored URL and its display name. Two releases spell
 * this differently: 2.6.50 stopped there, and its ids can therefore describe two different blobs at
 * once, which failed every publish for an account whose note had a replaced attachment. Later
 * releases fold in the content hash. Both spellings live here because the bundle decoder has to
 * recognise the old one to upgrade a 2.6.50 payload into the current shape.
 */
final class AttachmentLogicalIds {

    private AttachmentLogicalIds() {}

    /** The current derivation: one id per (note, position, URL, name, content). */
    @NonNull
    static String derive(
            @NonNull String noteStableId,
            int index,
            @NonNull String url,
            @NonNull String displayName,
            @NonNull String sha256) {
        return nameUuid(
                noteStableId + "\n" + index + "\n" + url + "\n" + displayName + "\n" + sha256);
    }

    /** The 2.6.50 derivation, kept only so its payloads can be recognised. */
    @NonNull
    static String deriveLegacy(
            @NonNull String noteStableId,
            int index,
            @NonNull String url,
            @NonNull String displayName) {
        return nameUuid(noteStableId + "\n" + index + "\n" + url + "\n" + displayName);
    }

    @NonNull
    private static String nameUuid(@NonNull String source) {
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
