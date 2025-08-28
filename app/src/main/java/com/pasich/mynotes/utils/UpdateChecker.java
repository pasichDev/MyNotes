package com.pasich.mynotes.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.pasich.mynotes.data.preferences.PreferenceHelper;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Клас для перевірки оновлень додатка
 */
@Singleton
public class UpdateChecker {

    private final Context context;
    private final PreferenceHelper preferenceHelper;

    @Inject
    public UpdateChecker(@ApplicationContext Context context, PreferenceHelper preferenceHelper) {
        this.context = context;
        this.preferenceHelper = preferenceHelper;
    }

    /**
     * Перевірити, чи є нова версія додатка
     * @return true, якщо є нова версія
     */
    public boolean hasNewVersion() {
        String currentVersion = getCurrentAppVersion();
        String lastKnownVersion = preferenceHelper.getLastKnownVersion();

        // Якщо lastKnownVersion порожній - це перший запуск
        if (lastKnownVersion == null || lastKnownVersion.isEmpty()) {
            return false;
        }
        
        return !currentVersion.equals(lastKnownVersion);
    }

    /**
     * Отримати поточну версію додатка
     * @return Версія додатка
     */
    public String getCurrentAppVersion() {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0.0";
        }
    }

    /**
     * Відзначити, що користувач ознайомився з оновленням
     */
    public void markVersionAsRead() {
        String currentVersion = getCurrentAppVersion();
        preferenceHelper.setLastKnownVersion(currentVersion);
    }

    /**
     * Ініціалізувати перевірку версії (викликати при першому запуску)
     */
    public void initializeVersionCheck() {
        String lastKnownVersion = preferenceHelper.getLastKnownVersion();
        if (lastKnownVersion == null || lastKnownVersion.isEmpty()) {
            // Перший запуск - зберігаємо поточну версію
            markVersionAsRead();
        }
    }
}
