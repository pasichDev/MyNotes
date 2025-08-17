package com.pasich.mynotes.base.view;

import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import io.reactivex.disposables.CompositeDisposable;

public interface BasePresenter<V extends BaseView> {

    void attachView(V mVIew);

    void viewIsReady();

    void detachView();

    DataManager getDataManager();

    CompositeDisposable getCompositeDisposable();

    SchedulerProvider getSchedulerProvider();
}
