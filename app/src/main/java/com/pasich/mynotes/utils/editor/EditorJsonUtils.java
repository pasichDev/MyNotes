package com.pasich.mynotes.utils.editor;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
                        JSONArray items = data.optJSONArray("items");
                        if (items != null) {
                            for (int j = 0; j < items.length(); j++) {
                                appendListItem(items.getJSONObject(j), plainText, "");
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
    private static void appendListItem(JSONObject item, StringBuilder builder, String indent) throws JSONException {
        String content = item.optString("content", "");
        if (!content.isEmpty()) {
            builder.append(indent).append("- ").append(content).append("\n");
        }

        JSONArray subItems = item.optJSONArray("items");
        if (subItems != null) {
            for (int i = 0; i < subItems.length(); i++) {
                appendListItem(subItems.getJSONObject(i), builder, indent + "  "); // додаємо відступ для вкладених елементів
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
