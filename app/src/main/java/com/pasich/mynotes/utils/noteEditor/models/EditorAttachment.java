package com.pasich.mynotes.utils.noteEditor.models;

import android.util.Log;

import com.google.gson.Gson;

public class EditorAttachment {
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

}
