package com.pasich.mynotes.utils.backup;


import android.util.Base64;

import com.google.gson.Gson;
import com.pasich.mynotes.data.model.backup.JsonBackup;

import java.nio.charset.StandardCharsets;

public class ScramblerBackupHelper {

    public static String encodeString(JsonBackup jsonBackup) {
        return Base64.encodeToString(new Gson().toJson(jsonBackup).getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
    }

    public static String getStringJson(String string){
        return new String(Base64.decode(string, Base64.DEFAULT), StandardCharsets.UTF_8);
    }

    public static JsonBackup decodeString(String string) {
        try {
            return new Gson().fromJson(new String(Base64.decode(string, Base64.DEFAULT), StandardCharsets.UTF_8), JsonBackup.class).setError(false);
        } catch (Exception e) {
            return new JsonBackup().error();
        }
    }


}
