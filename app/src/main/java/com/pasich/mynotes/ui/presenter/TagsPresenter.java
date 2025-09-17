package com.pasich.mynotes.ui.presenter;

import static com.pasich.mynotes.utils.constants.settings.TagSettings.MAX_TAG_COUNT;
import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_DEFAULT_TAGS_SORT_PREF;
import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_PREFERENCE_TAGS_SORT;

import android.util.Log;
import android.view.View;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.contract.TagsContract;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import com.preference.PowerPreference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;
import io.reactivex.disposables.CompositeDisposable;

@ActivityScoped
public class TagsPresenter extends BasePresenter<TagsContract.view> implements TagsContract.presenter {

    private List<Tag> cachedTags = new ArrayList<>();
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

        getCompositeDisposable().add(getDataManager().getTagsUser().subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(tagList -> {
            if (isViewAttached()) {
                // Створюємо спеціальний тег для кнопки "Додати"
                tagList.add(0, SystemTagsManager.createAddTag());

                // Зберігаємо в локальному кеші
                cachedTags = new ArrayList<>(tagList);

                // Відображаємо відсортований список
                displayTags();
            }
        }, throwable -> {
            Log.e("TagsPresenter", "Error loading tags", throwable);
            if (isViewAttached()) {
                getView().showToastMessage("Tag loading error");
            }
        }));
    }

    private void displayTags() {
        if (!isViewAttached() || cachedTags.isEmpty()) return;
        // Сортуємо локальний кеш згідно з налаштуваннями
        getView().loadTags(sortTagsList(cachedTags));
    }

    private List<Tag> sortTagsList(List<Tag> tagList) {
        String sortParam = PowerPreference.getDefaultFile().getString(ARGUMENT_PREFERENCE_TAGS_SORT, ARGUMENT_DEFAULT_TAGS_SORT_PREF);
        List<Tag> sortedList = new ArrayList<>(tagList);

        if (ARGUMENT_DEFAULT_TAGS_SORT_PREF.equals(sortParam)) {
            // Сортуємо за position (спеціальне сортування)
            sortedList.sort(Comparator.comparingInt(Tag::getPosition));
        } else {
            // Сортуємо за датою створення (ID - чим більший, тим новіший)
            sortedList.sort((tag1, tag2) -> Long.compare(tag2.getId(), tag1.getId()));
        }

        return sortedList;
    }

    @Override
    public void createTag(String tagName) {
        if (!isViewAttached() || tagName == null || tagName.trim().isEmpty()) return;
        Tag newTag = new Tag().create(tagName.trim());
        newTag.setVisibility(1);

        getCompositeDisposable().add(getDataManager().addTag(newTag).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
            if (isViewAttached()) {
                cachedTags.add(newTag);
                displayTags();
            }
        }, throwable -> {
            Log.e("TagsPresenter", "Error creating tag", throwable);
            if (isViewAttached()) {
                getView().showToastMessage("Error creating tag");
            }
        }));
    }


    @Override
    public void deleteTag(Tag tag) {
        if (!isViewAttached() || tag == null) return;

        getCompositeDisposable().add(getDataManager().deleteTag(tag).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
            if (isViewAttached()) {
                cachedTags.removeIf(t -> t.getId() == tag.getId());
                displayTags();
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

        tag.setVisibility(tag.getVisibility() == 1 ? 0 : 1);

        getCompositeDisposable().add(getDataManager().updateTag(tag).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
            if (isViewAttached()) {
                String message = tag.getVisibility() == 1 ? "Tag visible" : "Tag hidden";
                getView().showToastMessage(message);
                updateTagInCache(tag);
                displayTags();
            }
        }, throwable -> Log.e("TagsPresenter", "Error toggling tag visibility", throwable)));
    }

    @Override
    public void onAddTagClick() {
        if (isViewAttached()) {
            if (cachedTags.size() >= MAX_TAG_COUNT) {
                getView().showToastCheckCountTags();
            } else {
                getView().showCreateTagDialog();
            }
        }
    }

    @Override
    public void onTagLongClick(Tag tag, View anchorView) {
        if (isViewAttached() && tag != null && tag.getSystemAction() == 0) {
            getView().showTagOptionsDialog(tag, anchorView);
        }
    }

    @Override
    public void sortTags(String sortParam) {
        PowerPreference.getDefaultFile().setString(ARGUMENT_PREFERENCE_TAGS_SORT, sortParam);
        displayTags();
    }

    @Override
    public void onDragCompleted(List<Tag> currentTagOrders) {
        // Фільтруємо тільки користувацькі теги
        List<Tag> userTags = currentTagOrders.stream().filter(tag -> tag.getSystemAction() == 0).collect(Collectors.toList());

        // Оновлюємо позиції тільки для користувацьких тегів та видаляємо системні
        for (int i = 0; i < userTags.size(); i++) {
            userTags.get(i).setPosition(i);
        }

        // Оновлюємо локальний кеш
        updateCacheWithNewPositions(userTags);

        // Зберігаємо в БД
        getCompositeDisposable().add(getDataManager().updateTags(userTags).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
        }, throwable -> Log.e("TagsPresenter", "Error updating tag: ", throwable)));

    }

    @Override
    public void onSortMenuClick() {
        if (isViewAttached()) {
            getView().showSortDialog();
        }
    }

    // Допоміжні методи
    private void updateTagInCache(Tag updatedTag) {
        for (int i = 0; i < cachedTags.size(); i++) {
            if (cachedTags.get(i).getId() == updatedTag.getId()) {
                cachedTags.set(i, updatedTag);
                break;
            }
        }
    }

    private void updateCacheWithNewPositions(List<Tag> updatedTags) {
        for (Tag updatedTag : updatedTags) {
            updateTagInCache(updatedTag);
        }
    }

    @Override
    public void detachView() {
        super.detachView();
        cachedTags.clear(); // Очищаємо кеш при відключенні
    }
}