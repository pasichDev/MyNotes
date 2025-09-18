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

                cachedTags = new ArrayList<>(tagList);
                displayTags();
            }
        }, throwable -> {
            Log.e("TagsPresenter", "Error loading tags", throwable);
            if (isViewAttached()) {
                getView().showToastMessage("Tag loading error");
            }
        }));
    }


    // Сортуємо локальний кеш згідно з налаштуваннями
    public void displayTags() {
        if (!isViewAttached() || cachedTags.isEmpty()) return;
        getView().loadTags(sortTagsList(cachedTags));
    }


    private List<Tag> sortTagsList(List<Tag> tagList) {
        String sortParam = PowerPreference.getDefaultFile().getString(ARGUMENT_PREFERENCE_TAGS_SORT, ARGUMENT_DEFAULT_TAGS_SORT_PREF);
        List<Tag> sortedList = new ArrayList<>(tagList);

        sortedList.sort((o1, o2) -> {

            int x1 = o1.getSystemAction();
            int x2 = o2.getSystemAction();

            // Спеціальне сортування для системних міток addTag завжди перший
             if (o1.getSystemAction() == SystemTagsManager.SYSTEM_ACTION_ADD_TAG) x1 = 100;
             if (o2.getSystemAction() == SystemTagsManager.SYSTEM_ACTION_ADD_TAG) x2 = 100;


            int sComp = Math.toIntExact(x2 - x1);

            if (sComp != 0) {
                return sComp;
            }

            // Для користувацьких тегів використовуємо налаштування сортування
            if (o1.getSystemAction() == 0 && o2.getSystemAction() == 0) {
                if ("TagsPositionSort".equals(sortParam)) {
                    // Сортування за позицією (спеціальне)
                    return Integer.compare(o1.getPosition(), o2.getPosition());
                } else {
                    // Сортування за датою створення (ID - новіші вгорі)
                    return Long.compare(o2.getId(), o1.getId());
                }
            }

            // Для системних тегів за замовчуванням сортуємо за ID
            return Math.toIntExact(o2.getId() - o1.getId());
        });

        return sortedList;
    }

    @Override
    public void toggleTagVisibility(Tag tag) {
        if (!isViewAttached() || tag == null) return;

        tag.setVisibility(tag.getVisibility() == 1 ? 0 : 1);

        getCompositeDisposable().add(getDataManager().updateTag(tag).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
            if (isViewAttached()) {
                String message = tag.getVisibility() == 0 ? "Tag visible" : "Tag hidden";
                getView().showToastMessage(message);
                updateTagInCache(tag);
                displayTags();
            }
        }, throwable -> Log.e("TagsPresenter", "Error toggling tag visibility", throwable)));
    }

    @Override
    public void onAddTagClick() {
        if (isViewAttached()) {
            int minPosition = cachedTags.stream()
                    .mapToInt(Tag::getPosition)
                    .min()
                    .orElse(0);

            if (cachedTags.size() >= MAX_TAG_COUNT) {
                getView().showToastCheckCountTags();
            } else {
                getView().showCreateTagDialog(minPosition - 1);
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

    @Override
    public void getTagNotesCount(Tag tag, TagsContract.TagNotesCountCallback callback) {
        if (tag == null || callback == null) return;
        
        getCompositeDisposable().add(getDataManager()
                .getCountNotesTag(tag.getNameTag())
                .subscribeOn(getSchedulerProvider().io())
                .observeOn(getSchedulerProvider().ui())
                .subscribe(
                        callback::onTagNotesCountReceived,
                        throwable -> {
                            Log.e("TagsPresenter", "Error getting notes count for tag", throwable);
                            callback.onTagNotesCountReceived(0);
                        }
                ));
    }

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