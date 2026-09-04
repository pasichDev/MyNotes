package com.pasich.mynotes.extendedEditor.attach;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Moves a restored note's attachments into the folder its new row id owns.
 *
 * <p>A ZIP backup stores attachments under the note id they had when it was taken, and restore
 * extracts them verbatim. When that id is already in use the note is inserted under a fresh id,
 * which used to leave two notes sharing one {@code note_&lt;id&gt;} directory: saving the older
 * note then saw the restored note's files as orphans and deleted them.
 *
 * <p>Copies rather than moves, so a failure part-way leaves the original files exactly where the
 * pre-restore state expects them; the leftovers are ordinary orphans that cleanup reclaims later.
 * Deliberately free of {@code android.*} so the rewriting rules are unit-testable.
 */
public final class NoteAttachmentRelocator {

    /** The rewritten note fields, or the originals when nothing needed to move. */
    public static final class Result {
        @Nullable public final String attachmentsJson;
        @Nullable public final String valueJson;
        public final boolean changed;

        Result(@Nullable String attachmentsJson, @Nullable String valueJson, boolean changed) {
            this.attachmentsJson = attachmentsJson;
            this.valueJson = valueJson;
            this.changed = changed;
        }
    }

    private NoteAttachmentRelocator() {}

    /**
     * Repoints every reference from {@code previousNoteId} to {@code newNoteId}.
     *
     * @param attachmentsRoot the app-private {@code attachments} directory.
     * @param attachmentsJson the note's attachments column.
     * @param valueJson the note's Editor.js blocks, which carry their own copies of the URLs.
     */
    @NonNull
    public static Result relocate(
            @NonNull File attachmentsRoot,
            int previousNoteId,
            int newNoteId,
            @Nullable String attachmentsJson,
            @Nullable String valueJson) {
        if (previousNoteId <= 0 || newNoteId <= 0 || previousNoteId == newNoteId) {
            return new Result(attachmentsJson, valueJson, false);
        }
        String movedAttachments = attachmentsJson;
        boolean changed = false;

        if (attachmentsJson != null && !attachmentsJson.trim().isEmpty()) {
            try {
                JsonArray entries = JsonParser.parseString(attachmentsJson).getAsJsonArray();
                for (JsonElement element : entries) {
                    if (!element.isJsonObject()) continue;
                    JsonObject entry = element.getAsJsonObject();
                    if (!entry.has("url") || !entry.get("url").isJsonPrimitive()) continue;
                    String rewritten =
                            moveReference(
                                    attachmentsRoot,
                                    previousNoteId,
                                    newNoteId,
                                    entry.get("url").getAsString());
                    if (rewritten != null) {
                        entry.addProperty("url", rewritten);
                        changed = true;
                    }
                }
                if (changed) {
                    movedAttachments = entries.toString();
                }
            } catch (RuntimeException unreadable) {
                // Unreadable metadata is left exactly as it was; nothing here is worth guessing.
                return new Result(attachmentsJson, valueJson, false);
            }
        }

        String movedValueJson = valueJson;
        if (changed && valueJson != null && !valueJson.trim().isEmpty()) {
            try {
                JsonArray blocks = JsonParser.parseString(valueJson).getAsJsonArray();
                boolean rewroteBlock = false;
                for (JsonElement element : blocks) {
                    if (!element.isJsonObject()) continue;
                    JsonObject data = element.getAsJsonObject().getAsJsonObject("data");
                    if (data == null) continue;
                    JsonObject file = data.getAsJsonObject("file");
                    if (file == null || !file.has("url") || !file.get("url").isJsonPrimitive()) {
                        continue;
                    }
                    String rewritten =
                            moveReference(
                                    attachmentsRoot,
                                    previousNoteId,
                                    newNoteId,
                                    file.get("url").getAsString());
                    if (rewritten != null) {
                        file.addProperty("url", rewritten);
                        rewroteBlock = true;
                    }
                }
                if (rewroteBlock) {
                    movedValueJson = blocks.toString();
                }
            } catch (RuntimeException unreadable) {
                // Keep the blocks untouched rather than risk corrupting the note's content.
                movedValueJson = valueJson;
            }
        }

        return new Result(movedAttachments, movedValueJson, changed);
    }

    /**
     * Copies one referenced file into the new note's folder and returns its new URL.
     *
     * @return the rewritten URL, or {@code null} when the reference does not belong to the old note
     *     or its file is not there to copy.
     */
    @Nullable
    private static String moveReference(
            @NonNull File attachmentsRoot, int previousNoteId, int newNoteId, @NonNull String url) {
        AttachmentUrl parsed = AttachmentUrl.parse(url);
        if (parsed == null || !parsed.getNoteFolder().equals("note_" + previousNoteId)) {
            return null;
        }
        File source = parsed.resolveWithin(attachmentsRoot);
        if (source == null || !source.isFile()) {
            return null;
        }
        File targetFolder = new File(attachmentsRoot, "note_" + newNoteId);
        File target = new File(targetFolder, parsed.getFileName());
        try {
            if (!targetFolder.isDirectory() && !targetFolder.mkdirs()) {
                return null;
            }
            if (!target.exists()) {
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | SecurityException failure) {
            return null;
        }
        return AttachmentUrl.canonical(newNoteId, parsed.getFileName());
    }
}
