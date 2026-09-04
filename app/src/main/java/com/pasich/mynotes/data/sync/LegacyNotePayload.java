package com.pasich.mynotes.data.sync;

import androidx.annotation.NonNull;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pasich.mynotes.extendedEditor.attach.AttachmentUrl;
import com.pasich.mynotes.extendedEditor.attach.EditorAttachmentBlocks;
import java.util.ArrayList;
import java.util.List;

/**
 * Brings a note published by 2.6.50 into the shape the current store builds.
 *
 * <p>2.6.50 published a note's editor blocks with the writing device's own file URLs and derived
 * attachment ids without the content hash. The current store publishes wire references and ids that
 * include the hash, so an unchanged note read back from Drive after the upgrade hashed differently
 * from the same note rebuilt locally at the same timestamp. The merge reported a conflict against
 * itself — and, when the old version won the tiebreaker, applied it, rebuilt it in the new shape,
 * found nothing to publish because the merged snapshot equalled the remote one, and raised the same
 * conflict again on every sync until the user happened to edit the note.
 *
 * <p>The old shape carries everything the new derivation needs: the note's stable id, each block's
 * URL, each entry's name and hash. A payload is upgraded only when every manifest id is provably
 * the old derivation of exactly those inputs, so a payload written by any other rule is left alone.
 *
 * <p>Deliberately free of {@code android.*}: the equality it restores is what every sync depends
 * on.
 */
final class LegacyNotePayload {

    private LegacyNotePayload() {}

    /**
     * Rewrites {@code payload} in place when it is a 2.6.50-shaped note.
     *
     * @return true when the payload was upgraded.
     */
    static boolean upgrade(@NonNull String noteStableId, @NonNull JsonObject payload) {
        JsonArray manifest = payload.getAsJsonArray("attachmentsManifest");
        JsonElement blocks = payload.get("f");
        if (manifest == null
                || manifest.size() == 0
                || blocks == null
                || !blocks.isJsonPrimitive()
                || !blocks.getAsJsonPrimitive().isString()) {
            return false;
        }
        List<String> urls = EditorAttachmentBlocks.fileUrls(blocks.getAsString());
        if (urls.size() != manifest.size()) {
            return false;
        }
        List<String> newIds = new ArrayList<>(manifest.size());
        for (int index = 0; index < manifest.size(); index++) {
            JsonElement element = manifest.get(index);
            if (!element.isJsonObject()) {
                return false;
            }
            JsonObject entry = element.getAsJsonObject();
            String url = urls.get(index);
            if (AttachmentUrl.parse(url) == null
                    || !isString(entry.get("id"))
                    || !isString(entry.get("sha256"))
                    || !isString(entry.get("displayName"))) {
                return false;
            }
            String displayName = entry.get("displayName").getAsString();
            if (!entry.get("id")
                    .getAsString()
                    .equals(
                            AttachmentLogicalIds.deriveLegacy(
                                    noteStableId, index, url, displayName))) {
                return false;
            }
            newIds.add(
                    AttachmentLogicalIds.derive(
                            noteStableId,
                            index,
                            url,
                            displayName,
                            entry.get("sha256").getAsString()));
        }

        JsonObject names = new JsonObject();
        for (int index = 0; index < manifest.size(); index++) {
            JsonObject entry = manifest.get(index).getAsJsonObject();
            entry.addProperty("id", newIds.get(index));
            names.addProperty(newIds.get(index), entry.get("displayName").getAsString());
        }
        payload.add("attachmentNames", names);
        int[] position = {0};
        payload.addProperty(
                "f",
                EditorAttachmentBlocks.rewriteUrls(
                        blocks.getAsString(),
                        url -> AttachmentWireUrl.forLogicalId(newIds.get(position[0]++))));
        return true;
    }

    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }
}
