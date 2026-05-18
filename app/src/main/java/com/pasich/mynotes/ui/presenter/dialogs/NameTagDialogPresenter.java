package com.pasich.mynotes.ui.presenter.dialogs;

import android.util.Log;
import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.contract.dialogs.NameTagDialogContract;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import io.reactivex.disposables.CompositeDisposable;
import javax.inject.Inject;

public class NameTagDialogPresenter extends BasePresenter<NameTagDialogContract.view>
        implements NameTagDialogContract.presenter {

    @Inject
    public NameTagDialogPresenter(
            SchedulerProvider schedulerProvider,
            CompositeDisposable compositeDisposable,
            DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }

    @Override
    public void viewIsReady() {
        getView().initListeners();
    }

    @Override
    public void saveTag(Tag nameTag) {
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .addTag(nameTag)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        () -> {}, // onComplete
                                        throwable ->
                                                Log.e(
                                                        "NameTagDialogPresenter",
                                                        "Error saving tag",
                                                        throwable)));
    }

    @Override
    public void editNameTag(String nameNewTag, Tag mTag) {
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .renameTag(mTag, nameNewTag)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        () -> {}, // onComplete
                                        throwable ->
                                                Log.e(
                                                        "NameTagDialogPresenter",
                                                        "Error editing tag name",
                                                        throwable)));
    }
}
