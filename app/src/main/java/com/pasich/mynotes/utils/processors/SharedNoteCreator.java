package com.pasich.mynotes.utils.processors;


import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;

import javax.inject.Inject;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class SharedNoteCreator {

    private final DataManager dataManager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Inject
    public SharedNoteCreator(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public void create(String text, Callback callback) {
        disposables.add(dataManager.addNote(new Note().create("", text, System.currentTimeMillis(), ""), false).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(callback::onCreated, callback::onError));
    }

    public void clear() {
        disposables.clear();
    }


    public interface Callback {
        void onCreated(long id);

        void onError(Throwable error);
    }
}
