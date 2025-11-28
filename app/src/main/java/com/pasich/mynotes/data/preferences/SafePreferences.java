package com.pasich.mynotes.data.preferences;

import android.content.Context;
import android.content.SharedPreferences;


public class SafePreferences {

    private final SharedPreferences prefs;

    public SafePreferences(Context ctx) {
        this.prefs = ctx.getSharedPreferences(
                "com.pasich.mynotes_preferences",
                Context.MODE_PRIVATE
        );
    }

    public int getInt(String key, int def) {
        return prefs.getInt(key, def);
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
}
