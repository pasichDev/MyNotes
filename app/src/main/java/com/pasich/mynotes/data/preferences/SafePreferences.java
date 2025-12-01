package com.pasich.mynotes.data.preferences;

import android.content.Context;
import android.content.SharedPreferences;


public class SafePreferences {

    public static final String SHARED_PREFERENCE_FILE = "com.pasich.mynotes_preferences";
    private final SharedPreferences prefs;

    public SafePreferences(Context ctx) {
        this.prefs = ctx.getSharedPreferences(
                SHARED_PREFERENCE_FILE,
                Context.MODE_PRIVATE
        );
    }

    public static SharedPreferences raw(Context ctx) {
        return ctx.getSharedPreferences(
                SHARED_PREFERENCE_FILE,
                Context.MODE_PRIVATE
        );
    }


    public int getInt(String key, int def) {
        return prefs.getInt(key, def);
    }

    public float getFloat(String key, float def) {
        return prefs.getFloat(key, def);
    }

    public String getString(String key, String def) {
        return prefs.getString(key, def);
    }

    public boolean getBoolean(String key, boolean def) {
        return prefs.getBoolean(key, def);
    }

    public void putInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    public void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    public void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    public void putFloat(String key, float value) {
        prefs.edit().putFloat(key, value).apply();
    }
}
