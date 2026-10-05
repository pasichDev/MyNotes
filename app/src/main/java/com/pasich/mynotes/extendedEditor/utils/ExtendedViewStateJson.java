package com.pasich.mynotes.extendedEditor.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.pasich.mynotes.utils.editor.NoteViewState;
import com.pasich.mynotes.utils.editor.PositionRestorer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Translates the extended editor's position between the page's JSON and {@link NoteViewState}. Uses
 * Gson rather than org.json so it runs in plain JVM tests.
 */
public final class ExtendedViewStateJson {

    private ExtendedViewStateJson() {}

    /**
     * Reads the position the page reported ({@code currentViewState()} in runtime.js), or null when
     * it cannot be read.
     */
    @Nullable
    public static NoteViewState.Extended fromPage(@Nullable String json) {
        JsonObject page = object(json);
        if (page == null) return null;
        NoteViewState.Extended state = new NoteViewState.Extended();
        state.scrollTop = Math.max(0, intOf(page, "scrollTop", 0));
        state.scrollRatio = Math.max(0f, Math.min(1f, floatOf(page, "ratio")));
        state.topBlockId = stringOf(page, "topId");
        state.topBlockIndex = intOf(page, "topIndex", -1);
        state.topOffsetPx = intOf(page, "topOffset", 0);
        JsonElement caret = page.get("caret");
        if (caret != null && caret.isJsonObject()) {
            JsonObject c = caret.getAsJsonObject();
            state.caretBlockId = stringOf(c, "id");
            state.caretInput = Math.max(0, intOf(c, "input", 0));
            state.caretOffset = Math.max(0, intOf(c, "offset", 0));
        }
        return state;
    }

    /**
     * The page's position from the result of {@code currentViewStateJson()} run through {@code
     * WebView.evaluateJavascript}, which hands back the returned string JSON-encoded; null when the
     * page had no position to give.
     */
    @Nullable
    public static String fromScriptResult(@Nullable String result) {
        if (result == null) return null;
        try {
            JsonElement value = JsonParser.parseString(result);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
            String json = value.getAsString();
            return json.isEmpty() ? null : json;
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }

    /** The target in the form {@code restoreViewState()} in runtime.js takes. */
    @NonNull
    public static JsonObject toPage(@NonNull PositionRestorer.ExtendedTarget target) {
        JsonObject page = new JsonObject();
        if (target.caretBlockId != null) {
            page.addProperty("caretId", target.caretBlockId);
            page.addProperty("caretInput", target.caretInput);
            page.addProperty("caretOffset", target.caretOffset);
        }
        if (target.topBlockId != null) page.addProperty("topId", target.topBlockId);
        page.addProperty("topIndex", target.topBlockIndex);
        page.addProperty("topOffset", target.topOffsetPx);
        return page;
    }

    /** Ids of the blocks in an Editor.js document, in order; empty when there are none. */
    @NonNull
    public static List<String> blockIds(@Nullable String valueJson) {
        if (valueJson == null || valueJson.trim().isEmpty()) return Collections.emptyList();
        JsonArray blocks;
        try {
            JsonElement root = JsonParser.parseString(valueJson);
            if (root.isJsonObject() && root.getAsJsonObject().has("blocks")) {
                root = root.getAsJsonObject().get("blocks");
            }
            if (!root.isJsonArray()) return Collections.emptyList();
            blocks = root.getAsJsonArray();
        } catch (JsonParseException | IllegalStateException e) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>(blocks.size());
        for (JsonElement block : blocks) {
            String id = block.isJsonObject() ? stringOf(block.getAsJsonObject(), "id") : null;
            // A block without an id gets a new one on every render: it can only be matched by
            // index, so it keeps its place in the list without a usable id.
            ids.add(id != null ? id : "");
        }
        return ids;
    }

    @Nullable
    private static JsonObject object(@Nullable String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JsonElement root = JsonParser.parseString(json);
            return root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }

    @Nullable
    private static String stringOf(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()) return null;
        String text = value.getAsString();
        return text.isEmpty() ? null : text;
    }

    private static int intOf(JsonObject object, String name, int fallback) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return (int) Math.round(value.getAsDouble());
    }

    private static float floatOf(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return 0f;
        }
        float result = (float) value.getAsDouble();
        return Float.isNaN(result) ? 0f : result;
    }
}
