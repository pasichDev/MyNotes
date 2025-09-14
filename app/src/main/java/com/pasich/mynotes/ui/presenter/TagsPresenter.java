package com.pasich.mynotes.ui.presenter;

import static com.pasich.mynotes.utils.constants.settings.TagSettings.MAX_TAG_COUNT;

import android.util.Log;
import android.view.View;

import com.pasich.mynotes.R;
import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.contract.TagsContract;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;
import io.reactivex.disposables.CompositeDisposable;

@ActivityScoped
public class TagsPresenter extends BasePresenter<TagsContract.view> implements TagsContract.presenter {

    @Inject
    public TagsPresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }

    @Override
    public void viewIsReady() {
        if (isViewAttached()) {
            getView().settingsActionBar();
            getView().setupRecyclerView();
            loadTags();
        }
    }

    @Override
    public void loadTags() {
        if (!isViewAttached()) return;

        getCompositeDisposable().add(getDataManager().getTags().subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(tagList -> {
            if (isViewAttached()) {
                getView().loadTags(tagList);
            }
        }, throwable -> {
            Log.e("TagsPresenter", "Error loading tags", throwable);
            if (isViewAttached()) {
                getView().showToastMessage("Tag loading error");
            }
        }));
    }

    @Override
    public void createTag(String tagName) {
        if (!isViewAttached() || tagName == null || tagName.trim().isEmpty()) return;


        Tag newTag = new Tag().create(tagName.trim());
        newTag.setVisibility(1); // visibility = 1 (visible)

        getCompositeDisposable().add(getDataManager().addTag(newTag).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
            if (isViewAttached()) {
                loadTags();
            }
        }, throwable -> {
            Log.e("TagsPresenter", "Error creating tag", throwable);
            if (isViewAttached()) {
                getView().showToastMessage("Error creating tag");
            }
        }));
    }

    @Override
    public void editTag(Tag tag, String newName) {
        if (!isViewAttached() || tag == null || newName == null || newName.trim().isEmpty()) return;

        tag.setNameTag(newName.trim());

        getCompositeDisposable().add(getDataManager().updateTag(tag).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
            if (isViewAttached()) {
                loadTags();
            }
        }, throwable -> {
            Log.e("TagsPresenter", "Error updating tag", throwable);
            if (isViewAttached()) {
                getView().showToastMessage("Error updating tag");
            }
        }));
    }

    @Override
    public void deleteTag(Tag tag) {
        if (!isViewAttached() || tag == null) return;

        getCompositeDisposable().add(getDataManager().deleteTag(tag).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
            if (isViewAttached()) {
                loadTags();
            }
        }, throwable -> {
            Log.e("TagsPresenter", "Error deleting tag", throwable);
            if (isViewAttached()) {
                getView().showToastMessage("Error deleting tag");
            }
        }));
    }

    @Override
    public void toggleTagVisibility(Tag tag) {
        if (!isViewAttached() || tag == null) return;

        // Змінюємо видимість (1 -> 0, 0 -> 1)
        tag.setVisibility(tag.getVisibility() == 1 ? 0 : 1);

        getCompositeDisposable().add(getDataManager().updateTag(tag).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
            if (isViewAttached()) {
                int message = tag.getVisibility() == 1 ? R.string.toastTagVisible : R.string.toastTagHidde;
                getView().showToastMessage(message);
                loadTags();
            }
        }, throwable -> Log.e("TagsPresenter", "Error toggling tag visibility", throwable)));
    }

    @Override
    public void onAddTagClick() {
        if (isViewAttached()) {
            getCompositeDisposable().add(getDataManager().getCountTagAll().subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(integer -> {
                if (integer >= MAX_TAG_COUNT) {
                    getView().showToastCheckCountTags();
                } else getView().showCreateTagDialog();
            }));
        }
    }

    @Override
    public void onTagLongClick(Tag tag, View anchorView) {
        if (isViewAttached() && tag != null && tag.getSystemAction() == 0) {
            getView().showTagOptionsDialog(tag, anchorView);
        }
    }



    @Override
    public void detachView() {
        super.detachView();
    }
}