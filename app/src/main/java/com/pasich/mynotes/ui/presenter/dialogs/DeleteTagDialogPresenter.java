package com.pasich.mynotes.ui.presenter.dialogs;

import android.util.Log;
import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.contract.dialogs.DeleteTagDialogContract;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import io.reactivex.disposables.CompositeDisposable;
import javax.inject.Inject;

public class DeleteTagDialogPresenter extends BasePresenter<DeleteTagDialogContract.view>
        implements DeleteTagDialogContract.presenter {

    private static final String TAG = "DeleteTagDialogPresenter";

    private int countNotesForTag;

    @Inject
    public DeleteTagDialogPresenter(
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
    public void getLoadCountNotesForTag(String nameTag) {
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .getCountNotesTag(nameTag)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        this::setCountNotesForTag,
                                        error -> Log.e(TAG, "count notes for tag failed", error)));
    }

    @Override
    public void deleteTagUnchecked(Tag tag) {
        DetachedWrites.run(
                getDataManager().clearTagInNotes(tag),
                getSchedulerProvider().io(),
                TAG,
                "deleteTagUnchecked");
    }

    @Override
    public void deleteTagAndNotes(Tag tag) {
        DetachedWrites.run(
                getDataManager().deleteTagAndMoveNotesToTrash(tag),
                getSchedulerProvider().io(),
                TAG,
                "deleteTagAndNotes");
    }

    @Override
    public int getCountNotesForTag() {
        return this.countNotesForTag;
    }

    public void setCountNotesForTag(int count) {
        this.countNotesForTag = count;
        runOnView(view -> view.showNotesCount(count));
    }

    @Override
    public void detachView() {
        super.detachView();
        countNotesForTag = 0;
    }
}
