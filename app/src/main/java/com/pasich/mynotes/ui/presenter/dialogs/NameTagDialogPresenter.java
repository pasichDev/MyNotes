package com.pasich.mynotes.ui.presenter.dialogs;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.contract.dialogs.NameTagDialogContract;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import io.reactivex.disposables.CompositeDisposable;
import javax.inject.Inject;

public class NameTagDialogPresenter extends BasePresenter<NameTagDialogContract.view>
        implements NameTagDialogContract.presenter {

    private static final String TAG = "NameTagDialogPresenter";

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
        DetachedWrites.run(
                getDataManager().addTag(nameTag), getSchedulerProvider().io(), TAG, "saveTag");
    }

    @Override
    public void editNameTag(String nameNewTag, Tag mTag) {
        DetachedWrites.run(
                getDataManager().renameTag(mTag, nameNewTag),
                getSchedulerProvider().io(),
                TAG,
                "editNameTag");
    }
}
