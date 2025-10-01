package com.pasich.mynotes.utils.editor;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Utility class for converting between plain text and Editor.js JSON format
 */

public class ContentConverter {
    private static final String TAG = "ContentConverter";
    
    /**
     * Check if content is in Editor.js JSON format
     */
    public static boolean isRichContent(String content) {
        if (TextUtils.isEmpty(content)) {
            return false;
        }
        
        try {
            JSONObject json = new JSONObject(content);
            return json.has("blocks") && json.has("version");
        } catch (JSONException e) {
            return false;
        }
    }
    
    /**
     * Convert Editor.js JSON to plain text
     */
    public static String jsonToPlainText(String jsonContent) {
        if (TextUtils.isEmpty(jsonContent) || !isRichContent(jsonContent)) {
            return jsonContent != null ? jsonContent : "";
        }
        
        try {
            JSONObject json = new JSONObject(jsonContent);
            JSONArray blocks = json.getJSONArray("blocks");
            StringBuilder plainText = new StringBuilder();
            
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.getJSONObject(i);
                String blockType = block.getString("type");
                JSONObject data = block.getJSONObject("data");
                
                switch (blockType) {
                    case "paragraph":
                        if (data.has("text")) {
                            plainText.append(data.getString("text"));
                        }
                        break;
                    case "header":
                        if (data.has("text")) {
                            plainText.append(data.getString("text"));
                        }
                        break;
                    case "list":
                        if (data.has("items")) {
                            JSONArray items = data.getJSONArray("items");
                            String style = data.optString("style", "unordered");
                            for (int j = 0; j < items.length(); j++) {
                                if (style.equals("ordered")) {
                                    plainText.append((j + 1)).append(". ");
                                } else {
                                    plainText.append("• ");
                                }
                                plainText.append(items.getString(j)).append("\n");
                            }
                            continue; // Skip adding extra newlines
                        }
                        break;
                    case "quote":
                        if (data.has("text")) {
                            plainText.append("\"").append(data.getString("text")).append("\"");
                        }
                        break;
                    case "code":
                        if (data.has("code")) {
                            plainText.append("```\n").append(data.getString("code")).append("\n```");
                        }
                        break;
                    case "delimiter":
                        plainText.append("---");
                        break;
                    default:
                        Log.w(TAG, "Unknown block type: " + blockType);
                        break;
                }
                
                // Add newline between blocks (except for lists which handle it internally)
                if (i < blocks.length() - 1 && !blockType.equals("list")) {
                    plainText.append("\n\n");
                } else if (blockType.equals("list") && i < blocks.length() - 1) {
                    plainText.append("\n");
                }
            }
            
            return plainText.toString().trim();
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to convert JSON to plain text", e);
            return jsonContent;
        }
    }
    
    /**
     * Convert plain text to Editor.js JSON format
     */
    public static String plainTextToJson(String plainText) {
        if (TextUtils.isEmpty(plainText)) {
            return createEmptyEditorData();
        }
        
        try {
            JSONObject editorData = new JSONObject();
            editorData.put("time", System.currentTimeMillis());
            editorData.put("version", "2.28.2");
            
            JSONArray blocks = new JSONArray();
            String[] lines = plainText.split("\n");
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                JSONObject block = new JSONObject();
                JSONObject data = new JSONObject();
                
                // Simple heuristic: if line is short and doesn't contain punctuation, make it a header
                if (line.length() < 50 && !line.contains(".") && !line.contains(",") && 
                    !line.startsWith("•") && !line.matches("^\\d+\\..*")) {
                    block.put("type", "header");
                    data.put("text", line);
                    data.put("level", 2);
                } else if (line.startsWith("•") || line.matches("^\\d+\\..*")) {
                    // Handle lists (simplified - just convert to paragraph for now)
                    block.put("type", "paragraph");
                    data.put("text", line);
                } else {
                    block.put("type", "paragraph");
                    data.put("text", line);
                }
                
                block.put("data", data);
                blocks.put(block);
            }
            
            editorData.put("blocks", blocks);
            return editorData.toString();
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to convert plain text to JSON", e);
            return createEmptyEditorData();
        }
    }
    
    /**
     * Create empty Editor.js data structure
     */
    public static String createEmptyEditorData() {
        try {
            JSONObject editorData = new JSONObject();
            editorData.put("time", System.currentTimeMillis());
            editorData.put("version", "2.28.2");
            editorData.put("blocks", new JSONArray());
            return editorData.toString();
        } catch (JSONException e) {
            return "{\"time\":0,\"blocks\":[],\"version\":\"2.28.2\"}";
        }
    }
}