package com.pasich.mynotes.extendedEditor.models;

import android.util.Log;
import com.google.gson.Gson;
import org.json.JSONObject;

public class EditorAttachment {
    /** Immutable logical attachment identity; SHA-256 identifies only the shared blob bytes. */
    public String id;

    public String url;
    public String name;
    public String extension;
    public long size;

    public EditorAttachment(String url, String name, String extension, long size) {
        this.url = url;
        this.name = name;
        this.extension = extension;
        this.size = size;
    }

    public EditorAttachment(String url) {
        this.url = url;
    }

    public static EditorAttachment parseSingleAttachment(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        try {
            Gson gson = new Gson();
            return gson.fromJson(json, EditorAttachment.class);
        } catch (Exception e) {
            Log.e("jsonToModel", "Failed to parse single attachment JSON", e);
            return null;
        }
    }

    public static EditorAttachment fromJsonObject(JSONObject fileObj) {
        if (fileObj == null) return null;

        return new EditorAttachment(
                fileObj.optString("url", ""),
                fileObj.optString("name", ""),
                fileObj.optString("extension", ""),
                fileObj.optLong("size", 0));
    }
}
