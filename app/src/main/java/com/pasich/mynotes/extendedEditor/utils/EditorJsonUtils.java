package com.pasich.mynotes.extendedEditor.utils;

import android.util.Log;

import com.pasich.mynotes.extendedEditor.models.EditorAttachment;
import com.pasich.mynotes.extendedEditor.models.ParsedNote;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

public class EditorJsonUtils {

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
                        plainText.append(data.optString("text", ""));
                        break;

                    case "list":
                        handleListBlock(data, plainText);
                        break;

                    case "attaches":
                        handleAttachBlock(data, result);
                        break;

                    default:
                        // ignore others
                        break;
                }

                plainText.append("\n");
            }

        } catch (Exception e) {
            Log.e("jsonToModel", "Failed parsing blocks", e);
        }

        result.plainText = plainText.toString().trim();
        return result;
    }

    private static void handleListBlock(JSONObject data, StringBuilder plainText) throws JSONException {

        String style = data.optString("style", "unordered");
        JSONArray items = data.optJSONArray("items");

        if (items == null) return;

        for (int j = 0; j < items.length(); j++) {
            appendListItem(items.getJSONObject(j), plainText, "", style, j + 1);
        }
    }

    private static void handleAttachBlock(JSONObject data, ParsedNote result) {
        try {
            JSONObject fileObj = data.optJSONObject("file");
            if (fileObj == null) return;

            String url = fileObj.optString("url", "");
            String name = fileObj.optString("name", "");
            String extension = fileObj.optString("extension", "");
            long size = fileObj.optLong("size", 0);

            result.attachments.add(
                    new EditorAttachment(url, name, extension, size)
            );

        } catch (Exception e) {
            Log.e("jsonToModel", "Failed parsing attaches", e);
        }
    }


    /**
     * Рекурсивний метод для обробки вкладених елементів списку
     *
     * @param item    JSONObject одного елемента списку
     * @param builder StringBuilder для збору тексту
     * @param indent  відступ для вкладеності
     */

    private static void appendListItem(JSONObject item, StringBuilder builder, String indent, String style, int orderIndex) throws JSONException {
        String content = cleanText(item.optString("content", ""));
        if (!content.isEmpty()) {
            switch (style) {
                case "unordered":
                    builder.append(indent).append("- ").append(content).append("\n");
                    break;
                case "ordered":
                    builder.append(indent).append(orderIndex).append(". ").append(content).append("\n");
                    break;
                case "checklist":
                    boolean checked = item.optJSONObject("meta") != null && Objects.requireNonNull(item.optJSONObject("meta")).optBoolean("checked", false);
                    builder.append(indent).append(checked ? "[x] " : "[ ] ").append(content).append("\n");
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

    private static String cleanText(String text) {
        if (text == null) return "";
        // Заміна <br> або <br/> на новий рядок
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replace("&nbsp;", " ");
        // Можна додатково видалити інші HTML-теги, якщо потрібно
        text = text.replaceAll("<[^>]+>", "");
        return text;
    }

}
