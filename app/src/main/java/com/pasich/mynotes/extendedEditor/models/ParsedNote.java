package com.pasich.mynotes.extendedEditor.models;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ParsedNote {
    public String plainText = "";
    public List<EditorAttachment> attachments = new ArrayList<>();

    public static List<EditorAttachment> parseAttachmentsJson(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            Gson gson = new Gson();
            Type type = new TypeToken<List<EditorAttachment>>() {}.getType();
            return gson.fromJson(json, type);
        } catch (Exception e) {
            Log.e("jsonToModel", "Failed to parse attachments JSON", e);
            return new ArrayList<>();
        }
    }

}
