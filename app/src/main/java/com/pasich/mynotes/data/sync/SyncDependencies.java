package com.pasich.mynotes.data.sync;

import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.preferences.PreferenceHelper;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@EntryPoint
@InstallIn(SingletonComponent.class)
public interface SyncDependencies {
    AppDatabase database();

    PreferenceHelper preferenceHelper();
}
