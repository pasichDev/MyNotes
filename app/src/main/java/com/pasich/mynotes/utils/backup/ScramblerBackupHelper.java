package com.pasich.mynotes.utils.backup;


import android.util.Base64;

import com.google.gson.Gson;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.backup.JsonBackup;

import java.nio.charset.StandardCharsets;

public class ScramblerBackupHelper {

    public static String encodeString(JsonBackup jsonBackup) {
        try {
            String jsonString = new Gson().toJson(jsonBackup);
            return Base64.encodeToString(jsonString.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
        } catch (Exception e) {
            return "";
        }
    }


    public static JsonBackup decodeString(String string) {
        try {

            byte[] decodedBytes = Base64.decode(string, Base64.DEFAULT);

            String jsonString = new String(decodedBytes, StandardCharsets.UTF_8);

            boolean hasPositionField = jsonString.contains("\"e\":");

            JsonBackup result = new Gson().fromJson(jsonString, JsonBackup.class);

            if (result != null) {
                result.setError(false);

                if (result.getTags() != null && !result.getTags().isEmpty()) {
                    for (int i = 0; i < result.getTags().size(); i++) {
                        Tag tag = result.getTags().get(i);
                        if (!hasPositionField && tag.getSystemAction() == 0) {
                            tag.setPosition(-1);
                        }
                    }
                }
            } else {
                return new JsonBackup().error();
            }

            return result;
        } catch (Exception e) {
            return new JsonBackup().error();
        }
    }


}
