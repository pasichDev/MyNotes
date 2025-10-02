package com.pasich.mynotes.di;


import static com.pasich.mynotes.data.database.AppDatabase.MIGRATION_4_5;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.pasich.mynotes.cache.AppPreferencesCache;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.data.AppDataManager;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.AppDbHelper;
import com.pasich.mynotes.data.database.DbHelper;
import com.pasich.mynotes.data.preferences.AppPreferencesHelper;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import com.pasich.mynotes.utils.constants.DatabaseConstants;

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
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, DatabaseConstants.DB_NAME)
                .addCallback(sRoomDatabaseCallback)
                .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, MIGRATION_4_5).build();
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
    DataManager providesDataManager(AppDataManager appDataManager) {
        return appDataManager;
    }

    @Provides
    @Singleton
    PreferenceHelper providesPreferenceHelper(AppPreferencesHelper appPreferencesHelper) {
        return appPreferencesHelper;
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
    ThemePreferencesCache providesThemePreferencesCache() {
        final ThemePreferencesCache themePreferencesCache = new ThemePreferencesCache();
        themePreferencesCache.initialize();
        return themePreferencesCache;
    }

    @Provides
    @Singleton
    AppPreferencesCache providesAppPreferencesCache() {
        final AppPreferencesCache appPreferencesCache = new AppPreferencesCache();
        appPreferencesCache.initialize();
        return appPreferencesCache;
    }


}
