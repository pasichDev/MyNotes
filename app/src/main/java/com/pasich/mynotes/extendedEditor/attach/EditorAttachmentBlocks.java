package com.pasich.mynotes.extendedEditor.attach;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

/**
 * The one walk over an Editor.js document that knows where attachment references live.
 *
 * <p>A block carries its file either as {@code data.file} (the attaches and image tools) or as
 * {@code data.files[]}; the block type is deliberately not consulted, because a reference is a
 * reference whichever tool wrote it. Three separate walkers used to exist — one filtered by type,
 * one ignored {@code files[]}, one did both — so the same note could relocate correctly after a ZIP
 * restore and render broken links after a Drive restore.
 *
 * <p>Deliberately free of {@code android.*}: sync hashes the rewritten document, so the rewrite has
 * to be provably deterministic under ordinary JVM tests.
 */
public final class EditorAttachmentBlocks {

    /** Maps one stored URL to its replacement, or returns {@code null} to leave it alone. */
    public interface UrlMapper {
        @Nullable
        String map(@NonNull String url);
    }

    private EditorAttachmentBlocks() {}

    /**
     * Rewrites every file URL the mapper has a replacement for.
     *
     * @return the rewritten document, or {@code valueJson} itself when nothing changed or the
     *     document could not be read. Returning the original string verbatim, rather than a
     *     re-serialization of it, keeps an untouched note byte-identical on every device.
     */
    @Nullable
    public static String rewriteUrls(@Nullable String valueJson, @NonNull UrlMapper mapper) {
        if (valueJson == null || valueJson.trim().isEmpty()) {
            return valueJson;
        }
        JsonArray blocks;
        try {
            blocks = JsonParser.parseString(valueJson).getAsJsonArray();
        } catch (RuntimeException unreadable) {
            return valueJson;
        }
        boolean changed = false;
        for (JsonObject file : fileObjects(blocks)) {
            String url = file.get("url").getAsString();
            String replacement = mapper.map(url);
            if (replacement != null && !replacement.equals(url)) {
                file.addProperty("url", replacement);
                changed = true;
            }
        }
        // JsonElement.toString(), not Gson.toJson(): the latter HTML-escapes text, and both
        // directions of a sync must serialize identically or the hashes disagree.
        return changed ? blocks.toString() : valueJson;
    }

    /** Every file URL in document order. */
    @NonNull
    public static List<String> fileUrls(@Nullable String valueJson) {
        List<String> urls = new ArrayList<>();
        if (valueJson == null || valueJson.trim().isEmpty()) {
            return urls;
        }
        try {
            for (JsonObject file :
                    fileObjects(JsonParser.parseString(valueJson).getAsJsonArray())) {
                urls.add(file.get("url").getAsString());
            }
        } catch (RuntimeException unreadable) {
            return new ArrayList<>();
        }
        return urls;
    }

    /** The file object of the block with {@code blockId}, or {@code null}. */
    @Nullable
    public static JsonObject findFile(@Nullable String valueJson, @Nullable String blockId) {
        if (valueJson == null || valueJson.isEmpty() || blockId == null || blockId.isEmpty()) {
            return null;
        }
        try {
            for (JsonElement element : JsonParser.parseString(valueJson).getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject block = element.getAsJsonObject();
                if (!block.has("id") || !blockId.equals(block.get("id").getAsString())) continue;
                List<JsonObject> files = fileObjectsOf(block);
                return files.isEmpty() ? null : files.get(0);
            }
        } catch (RuntimeException unreadable) {
            return null;
        }
        return null;
    }

    @NonNull
    private static List<JsonObject> fileObjects(@NonNull JsonArray blocks) {
        List<JsonObject> files = new ArrayList<>();
        for (JsonElement element : blocks) {
            if (element.isJsonObject()) {
                files.addAll(fileObjectsOf(element.getAsJsonObject()));
            }
        }
        return files;
    }

    /** {@code data.file} first, then {@code data.files[]}, each only when it carries a URL. */
    @NonNull
    private static List<JsonObject> fileObjectsOf(@NonNull JsonObject block) {
        List<JsonObject> files = new ArrayList<>();
        JsonElement dataElement = block.get("data");
        if (dataElement == null || !dataElement.isJsonObject()) {
            return files;
        }
        JsonObject data = dataElement.getAsJsonObject();
        JsonElement file = data.get("file");
        if (hasUrl(file)) {
            files.add(file.getAsJsonObject());
        }
        JsonElement list = data.get("files");
        if (list != null && list.isJsonArray()) {
            for (JsonElement candidate : list.getAsJsonArray()) {
                if (hasUrl(candidate)) {
                    files.add(candidate.getAsJsonObject());
                }
            }
        }
        return files;
    }

    private static boolean hasUrl(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return false;
        }
        JsonElement url = element.getAsJsonObject().get("url");
        return url != null && url.isJsonPrimitive() && url.getAsJsonPrimitive().isString();
    }
}
