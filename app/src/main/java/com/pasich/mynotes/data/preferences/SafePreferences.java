package com.pasich.mynotes.data.preferences;

import android.content.Context;
import android.content.SharedPreferences;

public class SafePreferences {

    public static final String SHARED_PREFERENCE_FILE = "com.pasich.mynotes_preferences";
    private final SharedPreferences prefs;

    public SafePreferences(Context ctx) {
        this.prefs = ctx.getSharedPreferences(SHARED_PREFERENCE_FILE, Context.MODE_PRIVATE);
    }

    public static SharedPreferences raw(Context ctx) {
        return ctx.getSharedPreferences(SHARED_PREFERENCE_FILE, Context.MODE_PRIVATE);
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

    /**
     * Writes several keys as one durable edit and reports whether it reached disk.
     *
     * <p>The per-key {@code putX} helpers above each call {@code apply()}, which is asynchronous
     * and per-key: a caller writing eleven of them could be killed with some keys stored and others
     * not, and a caller that then cleared a journal on the strength of those calls could lose the
     * lot. {@code commit()} returns only once the write is durable, so a journal can be cleared on
     * a {@code true} and kept on a {@code false}.
     *
     * @return true only when every value in {@code values} is durably stored.
     */
    public boolean commitAll(java.util.Map<String, Object> values) {
        SharedPreferences.Editor editor = prefs.edit();
        for (java.util.Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                // Matches putString(key, null), which removes the key and lets the default
                // apply. A backup whose JSON carries an explicit null for a string preference
                // must restore to defaults, not abort the whole restore.
                editor.remove(entry.getKey());
            } else if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof Float) {
                editor.putFloat(entry.getKey(), (Float) value);
            } else if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else {
                throw new IllegalArgumentException(
                        "Unsupported preference type for " + entry.getKey());
            }
        }
        return editor.commit();
    }
}
