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

    /** Turns one stored reference into its new URL, or {@code null} to leave it alone. */
    private interface ReferenceMover {
        @Nullable
        String move(@NonNull String url);
    }

    /**
     * Repoints every reference from {@code previousNoteId} to {@code newNoteId}, copying the files
     * out of the old note's live folder.
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
        return rewrite(
                attachmentsJson,
                valueJson,
                url -> moveReference(attachmentsRoot, previousNoteId, newNoteId, url));
    }

    /**
     * Moves a restored note's staged attachments into the folder of the id it actually received.
     *
     * <p>A backup's files are unpacked into a staging directory rather than the live folders, so
     * this runs for every restored note, whether or not its id changed. A file already present
     * under the same name in the destination is reused when it holds the same bytes and left alone
     * otherwise — the staged copy then gets a fresh name — so a restore can never overwrite a file
     * another note still shows. A reference with nothing staged falls back to the live-folder
     * relocation above, which is what a note restored by an older release relied on.
     *
     * @param stagingRoot the staging directory that stands in for {@code attachments}.
     * @param attachmentsRoot the app-private {@code attachments} directory.
     */
    @NonNull
    public static Result adoptStaged(
            @NonNull File stagingRoot,
            @NonNull File attachmentsRoot,
            int previousNoteId,
            int newNoteId,
            @Nullable String attachmentsJson,
            @Nullable String valueJson) {
        if (previousNoteId <= 0 || newNoteId <= 0) {
            return new Result(attachmentsJson, valueJson, false);
        }
        File stagedFolder = new File(stagingRoot, "note_" + previousNoteId);
        Result result =
                rewrite(
                        attachmentsJson,
                        valueJson,
                        url -> {
                            String adopted =
                                    adoptReference(
                                            stagedFolder,
                                            attachmentsRoot,
                                            previousNoteId,
                                            newNoteId,
                                            url);
                            if (adopted != null || previousNoteId == newNoteId) {
                                return adopted;
                            }
                            return moveReference(attachmentsRoot, previousNoteId, newNoteId, url);
                        });
        // Whatever the note did not reference was never going to be shown; it need not linger.
        deleteRecursively(stagedFolder);
        return result;
    }

    /** Rewrites the column first and, only when it changed, the blocks that mirror it. */
    @NonNull
    private static Result rewrite(
            @Nullable String attachmentsJson, @Nullable String valueJson, ReferenceMover decide) {
        // One answer per URL: the column and the blocks name the same files, and a mover that
        // picked a fresh name on a collision gave each of them a different copy — the cleaner
        // then deleted the one the column did not know and the block rendered a missing file.
        java.util.Map<String, java.util.Optional<String>> decided = new java.util.HashMap<>();
        ReferenceMover mover =
                url ->
                        decided.computeIfAbsent(
                                        url, key -> java.util.Optional.ofNullable(decide.move(key)))
                                .orElse(null);
        String movedAttachments = attachmentsJson;
        boolean changed = false;

        if (attachmentsJson != null && !attachmentsJson.trim().isEmpty()) {
            try {
                JsonArray entries = JsonParser.parseString(attachmentsJson).getAsJsonArray();
                for (JsonElement element : entries) {
                    if (!element.isJsonObject()) continue;
                    JsonObject entry = element.getAsJsonObject();
                    if (!entry.has("url") || !entry.get("url").isJsonPrimitive()) continue;
                    String url = entry.get("url").getAsString();
                    String rewritten = mover.move(url);
                    // A staged file adopted under the very same URL changes nothing worth storing.
                    if (rewritten != null && !rewritten.equals(url)) {
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
        if (changed) {
            // The blocks are what the editor renders; the shared walker leaves an unreadable
            // document untouched rather than risk corrupting the note's content.
            movedValueJson = EditorAttachmentBlocks.rewriteUrls(valueJson, mover::move);
        }

        return new Result(movedAttachments, movedValueJson, changed);
    }

    /**
     * Copies one staged file into the new note's folder and returns its new URL.
     *
     * @return the rewritten URL, or {@code null} when the reference does not belong to the old note
     *     or nothing was staged for it.
     */
    @Nullable
    private static String adoptReference(
            @NonNull File stagedFolder,
            @NonNull File attachmentsRoot,
            int previousNoteId,
            int newNoteId,
            @NonNull String url) {
        AttachmentUrl parsed = AttachmentUrl.parse(url);
        if (parsed == null || !parsed.getNoteFolder().equals("note_" + previousNoteId)) {
            return null;
        }
        File source = new File(stagedFolder, parsed.getFileName());
        if (!source.isFile()) {
            return null;
        }
        File targetFolder = new File(attachmentsRoot, "note_" + newNoteId);
        try {
            if (!targetFolder.isDirectory() && !targetFolder.mkdirs()) {
                return null;
            }
            File target = new File(targetFolder, parsed.getFileName());
            if (target.exists() && !sameContent(source, target)) {
                target = new File(targetFolder, uniqueName(parsed.getFileName()));
            }
            if (!target.exists()) {
                Files.copy(source.toPath(), target.toPath());
            }
            return AttachmentUrl.canonical(newNoteId, target.getName());
        } catch (IOException | SecurityException | IllegalArgumentException failure) {
            return null;
        }
    }

    /** Streams both files; a restore has no attachment size ceiling to load them whole under. */
    private static boolean sameContent(@NonNull File first, @NonNull File second)
            throws IOException {
        if (first.length() != second.length()) {
            return false;
        }
        try (java.io.InputStream left =
                        new java.io.BufferedInputStream(new java.io.FileInputStream(first));
                java.io.InputStream right =
                        new java.io.BufferedInputStream(new java.io.FileInputStream(second))) {
            byte[] leftBuffer = new byte[8192];
            byte[] rightBuffer = new byte[8192];
            while (true) {
                int leftRead = readFully(left, leftBuffer);
                int rightRead = readFully(right, rightBuffer);
                if (leftRead != rightRead
                        || !java.util.Arrays.equals(
                                java.util.Arrays.copyOf(leftBuffer, leftRead),
                                java.util.Arrays.copyOf(rightBuffer, rightRead))) {
                    return false;
                }
                if (leftRead < leftBuffer.length) {
                    return true;
                }
            }
        }
    }

    private static int readFully(@NonNull java.io.InputStream input, @NonNull byte[] buffer)
            throws IOException {
        int filled = 0;
        while (filled < buffer.length) {
            int read = input.read(buffer, filled, buffer.length - filled);
            if (read == -1) break;
            filled += read;
        }
        return filled;
    }

    /** {@code name.ext} becomes {@code name-<uuid>.ext}, still a safe single segment. */
    @NonNull
    private static String uniqueName(@NonNull String fileName) {
        String suffix = java.util.UUID.randomUUID().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName + "-" + suffix;
        }
        return fileName.substring(0, dot) + "-" + suffix + fileName.substring(dot);
    }

    private static void deleteRecursively(@NonNull File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
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
