package com.pasich.mynotes.data.history;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.pasich.mynotes.data.database.entities.NoteVersionEntity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.extendedEditor.attach.EditorAttachmentBlocks;
import java.util.HashSet;
import java.util.Set;

/**
 * The content a note gets when one of its versions is restored.
 *
 * <p>History keeps text, not files: an attachment the current note no longer has may already have
 * been deleted from the device, and a block pointing at it would render broken. So the version's
 * text comes back, and of its attachments only those the note still holds; {@link
 * #droppedAttachments} counts the rest so the screen can say so before the user confirms.
 *
 * <p>Free of {@code android.*}, so it is testable under ordinary JVM tests.
 */
public final class NoteVersionRestore {

    @NonNull public final String title;
    @NonNull public final String value;
    @NonNull public final String valueJson;
    @Nullable public final String attachments;
    public final int droppedAttachments;

    private NoteVersionRestore(
            @NonNull String title,
            @NonNull String value,
            @NonNull String valueJson,
            @Nullable String attachments,
            int droppedAttachments) {
        this.title = title;
        this.value = value;
        this.valueJson = valueJson;
        this.attachments = attachments;
        this.droppedAttachments = droppedAttachments;
    }

    @NonNull
    public static NoteVersionRestore plan(
            @NonNull Note current, @NonNull NoteVersionEntity version) {
        Set<String> available =
                new HashSet<>(EditorAttachmentBlocks.fileUrls(current.getValueJson()));
        available.addAll(attachmentUrls(current.getAttachments()));

        String versionJson = version.valueJson == null ? "" : version.valueJson;
        EditorAttachmentBlocks.Filtered filtered =
                EditorAttachmentBlocks.keepOnlyFiles(versionJson, available);
        String valueJson = filtered.valueJson == null ? "" : filtered.valueJson;

        // The attachment column lists the files the document references; only the ones still on
        // the device can be listed, which are exactly the current note's that the version uses.
        Set<String> referenced = new HashSet<>(EditorAttachmentBlocks.fileUrls(valueJson));
        return new NoteVersionRestore(
                version.title,
                version.value,
                valueJson,
                keepOnly(current.getAttachments(), referenced),
                filtered.removed);
    }

    /** Applies the plan to {@code note}, leaving its tag, reminder and pin as they are. */
    public void applyTo(@NonNull Note note) {
        note.setTitle(title);
        note.setValue(value);
        note.setValueJson(valueJson);
        note.setAttachments(attachments);
    }

    @NonNull
    private static Set<String> attachmentUrls(@Nullable String column) {
        Set<String> urls = new HashSet<>();
        JsonArray array = parse(column);
        if (array == null) return urls;
        for (JsonElement element : array) {
            String url = urlOf(element);
            if (url != null) urls.add(url);
        }
        return urls;
    }

    @Nullable
    private static String keepOnly(@Nullable String column, @NonNull Set<String> urls) {
        JsonArray array = parse(column);
        if (array == null) return column;
        JsonArray kept = new JsonArray();
        for (JsonElement element : array) {
            String url = urlOf(element);
            if (url != null && urls.contains(url)) kept.add(element);
        }
        return kept.size() == array.size() ? column : kept.toString();
    }

    @Nullable
    private static JsonArray parse(@Nullable String column) {
        if (column == null || column.trim().isEmpty()) return null;
        try {
            JsonElement root = JsonParser.parseString(column);
            return root.isJsonArray() ? root.getAsJsonArray() : null;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    @Nullable
    private static String urlOf(@NonNull JsonElement element) {
        if (!element.isJsonObject()) return null;
        JsonElement url = element.getAsJsonObject().get("url");
        return url != null && url.isJsonPrimitive() ? url.getAsString() : null;
    }
}
