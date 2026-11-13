package com.pasich.mynotes.extendedEditor.utils;

import android.util.Log;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.extendedEditor.models.EditorAttachment;
import com.pasich.mynotes.extendedEditor.models.ParsedNote;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class EditorJsonUtils {
    private static final String TAG = "EditorJsonUtils";

    /**
     * Returns attachment object (EditorAttachment) for a given blockId. Supports both formats: -
     * data.file - data.files[]
     */
    public static EditorAttachment findAttachmentByBlockId(Note note, String blockId) {
        if (note == null) return null;
        try {
            // The same walk sync and restore use, so a block one of them can see is a block
            // the editor can open.
            com.google.gson.JsonObject file =
                    com.pasich.mynotes.extendedEditor.attach.EditorAttachmentBlocks.findFile(
                            note.getValueJson(), blockId);
            return file == null
                    ? null
                    : EditorAttachment.fromJsonObject(new JSONObject(file.toString()));
        } catch (Exception e) {
            Log.e(TAG, "findAttachmentByBlockId() failed", e);
        }
        return null;
    }

    /**
     * Finds the Editor.js block ID that corresponds to the given attachment.
     *
     * <p>This method scans the note's valueJson structure and looks for an "attaches" block whose
     * file URL matches the attachment's URL. Supports both data.file.url and data.files[].url
     * formats depending on the Editor.js tool.
     *
     * @param note The note containing the serialized Editor.js blocks.
     * @param att The attachment with the target file URL.
     * @return The ID of the matching block, or null if not found.
     */
    public static String findBlockIdByAttachment(Note note, EditorAttachment att) {
        try {
            if (note == null || att == null || att.url == null) return null;

            String json = note.getValueJson();
            if (json == null || json.isEmpty()) return null;

            JSONArray blocks = new JSONArray(json);
            String targetUrl = att.url.trim();

            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.optJSONObject(i);
                if (block == null) continue;

                if (!"attaches".equals(block.optString("type"))) continue;

                JSONObject data = block.optJSONObject("data");
                if (data == null) continue;

                JSONObject file = data.optJSONObject("file");
                if (file != null) {
                    String blockUrl = file.optString("url", "").trim();
                    if (targetUrl.equals(blockUrl)) {
                        return block.optString("id");
                    }
                }

                JSONArray files = data.optJSONArray("files");
                if (files != null) {
                    for (int f = 0; f < files.length(); f++) {
                        JSONObject fObj = files.optJSONObject(f);
                        if (fObj == null) continue;

                        String blockUrl = fObj.optString("url", "").trim();
                        if (targetUrl.equals(blockUrl)) {
                            return block.optString("id");
                        }
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "findBlockIdByAttachment() failed", e);
        }

        return null;
    }

    /**
     * Converts an extended Editor.js JSON representation into a legacy note format.
     *
     * <p>This method parses the array of Editor.js blocks and extracts: - Combined plain text
     * content (paragraphs, headers, lists) - Attached files (from "attaches" blocks)
     *
     * <p>It effectively "flattens" rich structured content into a plain-text fallback while also
     * collecting attachment metadata into the ParsedNote model.
     *
     * <p>Supported block types: - paragraph → appended as cleaned text - header → appended as plain
     * text - list → appended as list items - attaches → extracted into attachment list
     *
     * <p>Unsupported blocks are safely ignored.
     *
     * @param jsonData Raw Editor.js blocks JSON array (valueJson)
     * @return ParsedNote containing plain text and parsed attachments
     */
    public static ParsedNote extendedNoteToOldNote(String jsonData) {
        ParsedNote result = new ParsedNote();

        if (jsonData == null || jsonData.isEmpty()) {
            return result;
        }

        StringBuilder plainText = new StringBuilder();

        try {
            JSONArray blocks = new JSONArray(jsonData);
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.getJSONObject(i);
                String type = block.optString("type");
                JSONObject data = block.optJSONObject("data");

                if (data == null) continue;

                switch (type) {
                    case "paragraph":
                        plainText.append(cleanText(data.optString("text", "")));
                        break;

                    case "header":
                    case "Headers":
                        plainText.append(cleanText(data.optString("text", "")));
                        break;

                    case "list":
                        handleListBlock(data, plainText);
                        break;

                    case "attaches":
                        handleAttachBlock(data, result);
                        break;

                    case "image":
                        handleImageBlock(data, result);
                        break;

                    default:
                        // ignore others
                        break;
                }

                plainText.append("\n");
            }

        } catch (Exception e) {
            Log.e(TAG, "extendedNoteToOldNote() failed", e);
        }

        result.plainText = plainText.toString().trim();
        return result;
    }

    /**
     * Parses a “list” type block and adds its elements to plain text.
     *
     * @param data JSON data of the Editor.js block
     * @param plainText accumulated plain text string
     */
    private static void handleListBlock(JSONObject data, StringBuilder plainText)
            throws JSONException {

        String style = data.optString("style", "unordered");
        JSONArray items = data.optJSONArray("items");

        if (items == null) return;

        for (int j = 0; j < items.length(); j++) {
            appendListItem(items.getJSONObject(j), plainText, "", style, j + 1);
        }
    }

    /**
     * Parses the “attaches” block and adds attachments to the ParsedNote model.
     *
     * @param data JSON data from the Editor.js block
     * @param result the result where all attachments are collected
     */
    private static void handleAttachBlock(JSONObject data, ParsedNote result) {
        try {
            JSONObject fileObj = data.optJSONObject("file");
            if (fileObj == null) return;

            EditorAttachment att = EditorAttachment.fromJsonObject(fileObj);
            result.attachments.add(att);

        } catch (Exception e) {
            Log.e(TAG, "handleAttachBlock() failed", e);
        }
    }

    /**
     * Parses the “image” block (ImageTool) and adds attachments into ParsedNote.
     *
     * <p>Expected structure: data.file.url → string
     */
    private static void handleImageBlock(JSONObject data, ParsedNote result) {
        try {
            JSONObject fileObj = data.optJSONObject("file");
            if (fileObj == null) return;

            // EditorAttachment already supports parsing JSON properly
            EditorAttachment att = EditorAttachment.fromJsonObject(fileObj);

            result.attachments.add(att);

        } catch (Exception e) {
            Log.e(TAG, "handleImageBlock() failed", e);
        }
    }

    /**
     * Recursive method for processing nested list items.
     *
     * @param item JSONObject of a single list item.
     * @param builder StringBuilder for collecting text.
     * @param indent Indentation for nesting.
     */
    private static void appendListItem(
            JSONObject item, StringBuilder builder, String indent, String style, int orderIndex)
            throws JSONException {
        String content = cleanText(item.optString("content", ""));
        if (!content.isEmpty()) {
            switch (style) {
                case "unordered":
                    builder.append(indent).append("- ").append(content).append("\n");
                    break;
                case "ordered":
                    builder.append(indent)
                            .append(orderIndex)
                            .append(". ")
                            .append(content)
                            .append("\n");
                    break;
                case "checklist":
                    boolean checked =
                            item.optJSONObject("meta") != null
                                    && Objects.requireNonNull(item.optJSONObject("meta"))
                                            .optBoolean("checked", false);
                    builder.append(indent)
                            .append(checked ? "[x] " : "[ ] ")
                            .append(content)
                            .append("\n");
                    break;
                default:
                    builder.append(indent).append(content).append("\n");
            }
        }

        JSONArray subItems = item.optJSONArray("items");
        if (subItems != null) {
            for (int i = 0; i < subItems.length(); i++) {
                appendListItem(subItems.getJSONObject(i), builder, indent + "  ", style, i + 1);
            }
        }
    }

    // The entities the editor writes into block HTML: what the page escapes (& < > and, in
    // attributes, quotes), non-breaking spaces, and numeric references.
    private static final Pattern ENTITY =
            Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|amp|lt|gt|quot|apos|nbsp);");

    /**
     * Turns the HTML of a paragraph, header or list item into plain text: line breaks become
     * newlines, tags are dropped and entities are decoded, so text such as {@code a < b && c} comes
     * back as it was typed and not as {@code a &lt; b &amp;&amp; c}.
     *
     * @param text Raw HTML/text content from Editor.js
     * @return Sanitized plain-text string
     */
    static String cleanText(String text) {
        if (text == null) return "";
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("<[^>]+>", "");
        return decodeEntities(text);
    }

    /** Decodes entities in one pass, so {@code &amp;lt;} gives {@code &lt;} and not {@code <}. */
    private static String decodeEntities(String text) {
        if (text.indexOf('&') < 0) return text;
        Matcher matcher = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        int last = 0;
        while (matcher.find()) {
            out.append(text, last, matcher.start());
            out.append(decodeEntity(matcher.group(1), matcher.group()));
            last = matcher.end();
        }
        out.append(text, last, text.length());
        return out.toString();
    }

    private static String decodeEntity(String name, String original) {
        switch (name) {
            case "amp":
                return "&";
            case "lt":
                return "<";
            case "gt":
                return ">";
            case "quot":
                return "\"";
            case "apos":
                return "'";
            case "nbsp":
                return " ";
            default:
                try {
                    boolean hex = name.charAt(1) == 'x' || name.charAt(1) == 'X';
                    int code = Integer.parseInt(name.substring(hex ? 2 : 1), hex ? 16 : 10);
                    if (code == 0xA0) return " ";
                    return Character.isValidCodePoint(code)
                            ? new String(Character.toChars(code))
                            : original;
                } catch (NumberFormatException e) {
                    return original;
                }
        }
    }
}
