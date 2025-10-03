package com.pasich.mynotes;

import android.app.Application;
import android.util.Log;

import com.pasich.mynotes.cache.ThemePreferencesCache;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;
import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;

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

        // Глобальний RxJava ErrorHandler для уникнення UndeliverableException
        RxJavaPlugins.setErrorHandler(e -> {
            if (e instanceof UndeliverableException) {
                Throwable cause = e.getCause();
                if (cause instanceof InterruptedException) {
                    return;
                }
                Log.w(TAG, "Undeliverable exception received", cause);
            } else {
                Log.w(TAG, "Unhandled RxJava exception", e);
            }
        });
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
