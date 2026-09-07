package com.pasich.mynotes.extendedEditor.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pasich.mynotes.extendedEditor.attach.EditorAttachmentBlocks;

/**
 * Whether an Editor.js document holds anything the user actually put there.
 *
 * <p>Emptiness used to be decided by comparing the serialized string against {@code "[]"}, which an
 * untouched editor never produces: opening a note writes a document with one empty paragraph in it,
 * so a note nobody typed into read as meaningful, was saved, and counted in the statistics. The
 * answer has to come from the parsed blocks, because what the editor serializes for an empty
 * document (block ids, a {@code time} field, a trailing paragraph) keeps changing.
 *
 * <p>Deliberately free of {@code android.*}, so the rule is testable under ordinary JVM tests.
 */
public final class EditorDocument {

    private EditorDocument() {}

    /**
     * Reports whether the document carries text or an attachment.
     *
     * <p>A block counts as content unless it is a text block whose text is blank: a paragraph or
     * header with nothing in it, or a list with no non-blank items. Anything else — a delimiter, a
     * table, a tool this build does not know — counts, because only the empty paragraph is written
     * without the user asking for it; everything else got there by a deliberate insertion.
     *
     * @param valueJson raw Editor.js block array, may be {@code null}
     * @return {@code false} only when the document is provably empty
     */
    public static boolean hasContent(@Nullable String valueJson) {
        if (valueJson == null || valueJson.trim().isEmpty()) {
            return false;
        }
        JsonArray blocks;
        try {
            blocks = JsonParser.parseString(valueJson).getAsJsonArray();
        } catch (RuntimeException unreadable) {
            // Unreadable but not empty: keep the note rather than discard data we cannot read.
            return true;
        }
        if (!EditorAttachmentBlocks.fileUrls(valueJson).isEmpty()) {
            return true;
        }
        for (JsonElement element : blocks) {
            if (!element.isJsonObject() || carriesContent(element.getAsJsonObject())) {
                return true;
            }
        }
        return false;
    }

    /** The negation of "a text block with blank text". */
    private static boolean carriesContent(@NonNull JsonObject block) {
        JsonElement dataElement = block.get("data");
        if (dataElement != null && dataElement.isJsonObject()) {
            JsonObject data = dataElement.getAsJsonObject();
            JsonElement text = data.get("text");
            if (isString(text) && !isBlank(text.getAsString())) {
                return true;
            }
            JsonElement items = data.get("items");
            if (items != null && items.isJsonArray() && anyItemFilled(items.getAsJsonArray())) {
                return true;
            }
        }
        // Nothing readable as text: only a text tool is allowed to be empty. Any other tool
        // is in the document because someone inserted it.
        return !isTextTool(block);
    }

    /** Walks list items, including the nested ones a checklist or sub-list can hold. */
    private static boolean anyItemFilled(@NonNull JsonArray items) {
        for (JsonElement element : items) {
            if (element.isJsonPrimitive()) {
                // The pre-2.x list tool stored plain strings instead of item objects.
                if (!isBlank(element.getAsString())) return true;
                continue;
            }
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            JsonElement content = item.get("content");
            if (isString(content) && !isBlank(content.getAsString())) {
                return true;
            }
            JsonElement nested = item.get("items");
            if (nested != null && nested.isJsonArray() && anyItemFilled(nested.getAsJsonArray())) {
                return true;
            }
        }
        return false;
    }

    /** Tools whose whole content is the text this class already read and found blank. */
    private static boolean isTextTool(@NonNull JsonObject block) {
        JsonElement type = block.get("type");
        if (!isString(type)) return false;
        switch (type.getAsString()) {
            case "paragraph":
            case "header":
            case "Headers":
            case "list":
            case "checklist":
                return true;
            default:
                return false;
        }
    }

    /** Blank once the markup an empty editor line leaves behind is taken out. */
    private static boolean isBlank(@Nullable String html) {
        if (html == null) return true;
        String text =
                html.replaceAll("(?i)<br\\s*/?>", " ")
                        .replaceAll("<[^>]+>", "")
                        .replace("&nbsp;", " ")
                        .replace('\u00a0', ' ')
                        .replace("\ufeff", "")
                        .replace("\u200b", "");
        return text.trim().isEmpty();
    }

    private static boolean isString(@Nullable JsonElement element) {
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString();
    }
}
