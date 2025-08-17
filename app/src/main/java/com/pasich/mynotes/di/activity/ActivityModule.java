package com.pasich.mynotes.di.activity;


import com.pasich.mynotes.ui.contract.BackupContract;
import com.pasich.mynotes.ui.contract.MainContract;
import com.pasich.mynotes.ui.contract.NoteContract;
import com.pasich.mynotes.ui.contract.TrashContract;
import com.pasich.mynotes.ui.presenter.BackupPresenter;
import com.pasich.mynotes.ui.presenter.MainPresenter;
import com.pasich.mynotes.ui.presenter.NotePresenter;
import com.pasich.mynotes.ui.presenter.TrashPresenter;
import com.pasich.mynotes.utils.rx.AppSchedulerProvider;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.scopes.ActivityScoped;
import io.reactivex.disposables.CompositeDisposable;

@Module
@InstallIn(ActivityComponent.class)
public class ActivityModule {


    @Provides
    CompositeDisposable provideCompositeDisposable() {
        return new CompositeDisposable();
    }

    @Provides
    SchedulerProvider provideScheduleProvider() {
        return new AppSchedulerProvider();
    }

    @Provides
    @ActivityScoped
    MainContract.presenter providesMainPresenter(MainPresenter presenter) {
        return presenter;
    }

    @Provides
    @ActivityScoped
    BackupContract.presenter providesBackupPresenter(BackupPresenter presenter) {
        return presenter;
    }

    @Provides
    @ActivityScoped
    NoteContract.presenter providesNotePresenter(NotePresenter presenter) {
        return presenter;
    }

    @Provides
    @ActivityScoped
    TrashContract.presenter providerTrashActivityPresenter(TrashPresenter presenter) {
        return presenter;
    }


}
