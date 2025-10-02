package com.pasich.mynotes.utils.editor;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

public class EditorJsonUtils {

    /**
     * Перетворює JSON від Editor.js у plain text.
     *
     * @param jsonData JSON рядок, що містить блоки без заголовка
     * @return plain text, об'єднаний з усіх блоків
     */
    public static String jsonToPlainText(String jsonData) {
        if (jsonData == null || jsonData.isEmpty()) return "";

        StringBuilder plainText = new StringBuilder();

        try {
            JSONArray blocks = new JSONArray(jsonData);

            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.getJSONObject(i);
                String type = block.optString("type");
                JSONObject data = block.optJSONObject("data");

                if (data == null) continue;

                switch (type) {
                    case "paragraph", "header":
                        plainText.append(cleanText(data.optString("text", "")));
                        break;

                    case "list":
                        String style = data.optString("style", "unordered");
                        JSONArray items = data.optJSONArray("items");
                        if (items != null) {
                            for (int j = 0; j < items.length(); j++) {
                                appendListItem(items.getJSONObject(j), plainText, "", style, j + 1);
                            }
                        }
                        break;

                    default:
                        // ігноруємо невідомі блоки
                        break;
                }

                plainText.append("\n"); // розділяємо блоки новим рядком
            }

        } catch (JSONException e) {
            Log.e("jsonToPlainText", "Failed to parse JSON", e);
        }

        return plainText.toString().trim();
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
