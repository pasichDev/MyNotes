package com.pasich.mynotes.di;

import static com.pasich.mynotes.data.database.AppDatabase.MIGRATION_4_5;
import static com.pasich.mynotes.data.database.AppDatabase.MIGRATION_5_6;
import static com.pasich.mynotes.data.database.AppDatabase.MIGRATION_6_7;
import static com.pasich.mynotes.data.database.AppDatabase.MIGRATION_7_8;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.annotation.Nullable;
import androidx.room.Room;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.pasich.mynotes.data.AppDataManager;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.database.AppDbHelper;
import com.pasich.mynotes.data.database.DbHelper;
import com.pasich.mynotes.data.preferences.AppPreferencesHelper;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import com.pasich.mynotes.data.preferences.SafePreferences;
import com.pasich.mynotes.utils.constants.DatabaseConstants;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public class ApplicationModule {

    /**
     * Returns null when the app was built without google-services.json.
     *
     * <p>The default FirebaseApp is created from resources the google-services plugin generates.
     * Calling {@code FirebaseAuth.getInstance()} unconditionally crashes such a build as soon as
     * any screen injects it.
     */
    @Provides
    @Singleton
    @Nullable
    FirebaseAuth providesFirebaseAuth(@ApplicationContext Context context) {
        FirebaseApp app = FirebaseApp.initializeApp(context);
        return app == null ? null : FirebaseAuth.getInstance(app);
    }

    @Provides
    @Singleton
    AppDatabase providesAppDatabase(@ApplicationContext Context context) {
        AppDatabase.setContext(context);
        return Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        DatabaseConstants.DB_NAME)
                .addMigrations(
                        AppDatabase.MIGRATION_2_3,
                        AppDatabase.MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        AppDatabase.MIGRATION_8_9,
                        AppDatabase.MIGRATION_9_10,
                        AppDatabase.MIGRATION_10_11,
                        AppDatabase.MIGRATION_11_12,
                        AppDatabase.MIGRATION_12_13,
                        AppDatabase.MIGRATION_13_14,
                        AppDatabase.MIGRATION_14_15,
                        AppDatabase.MIGRATION_15_16,
                        AppDatabase.MIGRATION_16_17,
                        AppDatabase.MIGRATION_17_18,
                        AppDatabase.MIGRATION_18_19)
                .build();
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
            PackageInfo packageInfo =
                    context.getPackageManager().getPackageInfo("com.android.vending", 0);
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

    @Provides
    @Singleton
    public Markwon provideMarkwon(@ApplicationContext Context context) {
        return Markwon.builder(context)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .build();
    }
}
