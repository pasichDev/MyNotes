package com.pasich.mynotes;

import android.app.Application;
import android.util.Log;
import com.pasich.mynotes.cache.NotificationPreferencesCache;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.utils.notification.NotificationChannelManager;
import dagger.hilt.android.HiltAndroidApp;
import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;
import java.util.concurrent.Executors;
import javax.inject.Inject;

@HiltAndroidApp
public class MyApp extends Application {

    private static final String TAG = "MyNotesApp";
    public static volatile float GLOBAL_FONT_SCALE = 1.0f;
    public static volatile boolean CACHE_READY = false;
    public static ThemePreferencesCache CACHE;
    @Inject ThemePreferencesCache themePreferencesCache;
    @Inject NotificationPreferencesCache notificationPreferencesCache;

    @Override
    public void onCreate() {
        super.onCreate();

        CACHE = themePreferencesCache;

        NotificationChannelManager.ensureChannel(this, notificationPreferencesCache);

        Executors.newSingleThreadExecutor()
                .execute(
                        () -> {
                            themePreferencesCache.initialize();
                            notificationPreferencesCache.initialize();
                            GLOBAL_FONT_SCALE = themePreferencesCache.getUiFontScale();
                            CACHE_READY = true;
                        });

        // Global RxJava ErrorHandler to avoid UndeliverableException
        RxJavaPlugins.setErrorHandler(
                e -> {
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
}
