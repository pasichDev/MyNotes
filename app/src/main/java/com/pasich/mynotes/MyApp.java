package com.pasich.mynotes;

import android.app.Application;
import android.util.Log;

import com.pasich.mynotes.cache.ThemePreferencesCache;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class MyApp extends Application {

    private static final String TAG = "MyApp";

    @Inject
    ThemePreferencesCache themePreferencesCache;


    @Override
    public void onCreate() {
        super.onCreate();
        // Ініціалізація кешу тем для покращення продуктивності
        initThemeCache();
    }

    /**
     * Ініціалізація кешу налаштувань тем для покращення продуктивності
     */
    private void initThemeCache() {
        try {
            themePreferencesCache.initialize();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize theme preferences cache", e);
        }
    }
}