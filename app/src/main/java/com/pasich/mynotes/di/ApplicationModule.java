package com.pasich.mynotes.di;


import static com.pasich.mynotes.data.database.AppDatabase.MIGRATION_4_5;
import static com.pasich.mynotes.data.database.AppDatabase.MIGRATION_5_6;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.room.Room;

import com.pasich.mynotes.data.AppDataManager;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.AppDbHelper;
import com.pasich.mynotes.data.database.DbHelper;
import com.pasich.mynotes.data.preferences.AppPreferencesHelper;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import com.pasich.mynotes.data.preferences.SafePreferences;
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
    AppDatabase providesAppDatabase(@ApplicationContext Context context) {
        AppDatabase.setContext(context);
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, DatabaseConstants.DB_NAME)
                .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build();
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
    public SafePreferences provideSafePreferences(@ApplicationContext Context context) {
        return new SafePreferences(context);
    }
}
