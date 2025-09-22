package com.pasich.mynotes.di;


import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.pasich.mynotes.cache.AppPreferencesCache;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.data.AppDataManger;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.AppDbHelper;
import com.pasich.mynotes.data.database.DbHelper;
import com.pasich.mynotes.data.preferences.AppPreferencesHelper;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import com.pasich.mynotes.utils.backup.CloudCacheHelper;
import com.pasich.mynotes.utils.constants.Database;
import com.pasich.mynotes.utils.constants.DriveScope;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class ApplicationModule {

    @Provides
    @Singleton
    AppDatabase providesAppDatabase(@ApplicationContext Context context, RoomDatabase.Callback sRoomDatabaseCallback) {
        AppDatabase.setContext(context);
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, Database.DB_NAME)
                .addCallback(sRoomDatabaseCallback)
                .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
                .build();
    }


    @Provides
    @Singleton
    RoomDatabase.Callback providerRoomDatabaseCallback() {
        return new RoomDatabase.Callback() {

            @Override
            public void onCreate(@NonNull SupportSQLiteDatabase db) {
                super.onCreate(db);
                // Системні мітки тепер управляються через SystemTagsManager
            }


            @Override
            public void onOpen(@NonNull SupportSQLiteDatabase db) {
                super.onOpen(db);
            }
        };
    }


    @Provides
    @Singleton
    DbHelper providesDbHelper(AppDbHelper appDbHelper) {
        return appDbHelper;
    }


    @Provides
    @Singleton
    DataManager providesDataManager(AppDataManger appDataManager) {
        return appDataManager;
    }

    @Provides
    @Singleton
    PreferenceHelper providesPreferenceHelper(AppPreferencesHelper appPreferencesHelper) {
        return appPreferencesHelper;
    }


    @Provides
    @Singleton
    Scope provideCloudAccessDriveScope() {
        return DriveScope.ACCESS_DRIVE_SCOPE;
    }


    @Provides
    @Singleton
    GoogleSignInClient providesGoogleSignInClient(@ApplicationContext Context mContext, Scope accessDrive) {
        return GoogleSignIn.getClient(mContext, new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestScopes(accessDrive).build());
    }

    @Provides
    @Singleton
    boolean providerIsPlayStoreInstalled(@ApplicationContext Context context) {
        boolean flag = false;
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo("com.android.vending", 0);
            assert packageInfo.applicationInfo != null;
            flag = packageInfo.applicationInfo.enabled;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return flag;
    }


    @Provides
    @Singleton
    CloudCacheHelper providesCloudCacheHelper(@ApplicationContext Context mContext, Scope accessDrive, boolean isPlayMarketInstall) {
        CloudCacheHelper helper = new CloudCacheHelper();
        // Запускаємо асинхронну ініціалізацію Google Services
        helper.initializeAsync(mContext, accessDrive, isPlayMarketInstall)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        android.util.Log.e("ApplicationModule", "Failed to initialize Google Services asynchronously", throwable);
                    } else {
                        android.util.Log.d("ApplicationModule", "Google Services initialized asynchronously");
                    }
                });

        return helper;
    }

    @Provides
    @Singleton
    ThemePreferencesCache providesThemePreferencesCache(@ApplicationContext Context context) {
        final ThemePreferencesCache themePreferencesCache = new ThemePreferencesCache(context);
        themePreferencesCache.initialize();
        return themePreferencesCache;
    }

    @Provides
    @Singleton
    AppPreferencesCache providesAppPreferencesCache(@ApplicationContext Context context) {
        final AppPreferencesCache appPreferencesCache = new AppPreferencesCache(context);
        appPreferencesCache.initialize();
        return appPreferencesCache;
    }


}
