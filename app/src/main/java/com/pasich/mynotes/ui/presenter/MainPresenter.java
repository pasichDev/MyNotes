package com.pasich.mynotes.ui.presenter;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.contract.MainContract;
import com.pasich.mynotes.ui.state.MainViewState;
import com.pasich.mynotes.ui.state.UiEvent;
import com.pasich.mynotes.utils.TagsSorter;
import com.pasich.mynotes.utils.constants.settings.SortParam;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.BehaviorSubject;

@ActivityScoped
public class MainPresenter extends BasePresenter<MainContract.view> implements MainContract.presenter {

    private static final String TAG = "MainPresenter";
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final BehaviorSubject<Tag> selectedTag =
            BehaviorSubject.createDefault(SystemTagsManager.createAllNotesTag());
    private final BehaviorSubject<String> sortParam = BehaviorSubject.create();
    private final BehaviorSubject<MainViewState> viewState = BehaviorSubject.create();
    private final BehaviorSubject<String> searchQuery =
            BehaviorSubject.createDefault("");

    private UiEvent lastUiEvent = UiEvent.NONE;
    private Note backupDeleteNote;
    private Observable<List<Tag>> tagsStream;
    private Observable<List<Note>> notesStream;


    @Inject
    public MainPresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }

    @Override
    public void viewIsReady() {
        sortParam.onNext(getDataManager().getSortParam());
        getView().settingsLists();
        getView().initListeners();
        initStreams();
        startStateCombiner();
        starListsRenderStream();
        startSearchStream();
    }

    void starListsRenderStream() {
        getCompositeDisposable().add(viewState.hide()
                .subscribeOn(getSchedulerProvider().io())
                .observeOn(getSchedulerProvider().ui())
                .subscribe(
                        state -> getView().render(state),
                        throwable -> Log.e(TAG, "observeState", throwable)
                )
        );
    }

    private void initStreams() {
        tagsStream = Observable.combineLatest(
                getDataManager().getTags()
                        .toObservable()
                        .map(tagList -> TagsSorter.sortTags(tagList, getDataManager().getSortParamTags())),
                selectedTag,
                (tags, selected) ->
                        tags.stream()
                                .map(t -> {
                                    Tag copy = t.copy();
                                    copy.setSelected(selected != null && selected.getId() == t.getId());
                                    return copy;
                                })
                                .collect(Collectors.toList())
        );

        notesStream = getDataManager().getNotes()
                .toObservable();
    }

    private void startStateCombiner() {
        getCompositeDisposable().add(
                Observable.combineLatest(tagsStream, notesStream, selectedTag, sortParam, this::buildState)
                        .debounce(5, TimeUnit.MILLISECONDS)
                        .distinctUntilChanged()
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(state -> getView().render(state), throwable -> Log.e(TAG, "combineLatest", throwable))
        );
    }

    private MainViewState buildState(
            List<Tag> tags,
            List<Note> notes,
            Tag selectedTag,
            String sort
    ) {

        // Build hidden tags set
        Set<String> hidden = new HashSet<>();
        for (Tag t : tags) {
            if (t.getVisibility() == 1) {
                hidden.add(t.getNameTag());
            }
        }

        // Filter hidden
        List<Note> visible = new ArrayList<>(notes.size());
        if (hidden.isEmpty()) {
            visible.addAll(notes);
        } else {
            for (Note n : notes) {
                if (!hidden.contains(n.getTag())) {
                    visible.add(n);
                }
            }
        }

        // Determine if we show ALL notes
        boolean isAllNotes = selectedTag == null || selectedTag.getSystemAction() == SystemTagsManager.SYSTEM_ACTION_ALL_NOTES;

        // Filter by selected tag
        List<Note> filtered;
        if (isAllNotes) {
            filtered = visible;
        } else {
            String tagName = selectedTag.getNameTag();
            filtered = new ArrayList<>();
            for (Note n : visible) {
                if (tagName.equals(n.getTag())) {
                    filtered.add(n);
                }
            }
        }

        // Copy before sort
        List<Note> sorted = new ArrayList<>(filtered);

        // Sort
        boolean sortByNew = SortParam.DataSort.equals(sort);
        sorted.sort((a, b) ->
                sortByNew
                        ? Long.compare(b.getDate(), a.getDate())   // newest first
                        : Long.compare(a.getDate(), b.getDate())   // oldest first
        );


        return new MainViewState(tags, sorted, selectedTag, lastUiEvent);
    }

    private void startSearchStream() {
        getCompositeDisposable().add(
                Observable.combineLatest(
                                notesStream,
                                searchQuery,
                                this::filterNotes
                        )
                        .debounce(150, TimeUnit.MILLISECONDS)
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(
                                filtered -> getView().renderSearch(filtered),
                                throwable -> Log.e(TAG, "search error", throwable)
                        )
        );
    }

    private List<Note> filterNotes(List<Note> notes, String query) {
        if (query == null || query.trim().length() < 2)
            return Collections.emptyList();

        String q = query.toLowerCase().trim();

        return notes.stream()
                .filter(n ->
                        n.getTitle().toLowerCase().contains(q) ||
                                n.getValue().toLowerCase().contains(q)
                )
                .collect(Collectors.toList());
    }

    public void updateSearchQuery(String query) {
        searchQuery.onNext(query);
    }

    @Override
    public void onSortChanged(String newSort) {
        lastUiEvent = UiEvent.SORT_CHANGED;
        sortParam.onNext(newSort);
    }

    @Override
    public void onTagSelected(Tag tag) {
        lastUiEvent = UiEvent.TAG_CHANGED;
        selectedTag.onNext(tag);
    }

    @Override
    public void clearUiEvent() {
        lastUiEvent = UiEvent.NONE;
    }

    @Override
    public void newNotesClick() {
        if (!isViewAttached()) return;
        getCompositeDisposable().add(
                getDataManager().addNote(new Note().create("", "", System.currentTimeMillis(), ""), false)
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(id -> {
                            lastUiEvent = UiEvent.NOTE_CREATED;
                            new Handler(Looper.getMainLooper()).postDelayed(() -> getView().openNewNoteWithId(id), 80);
                        }, throwable -> Log.e(TAG, "Failed to create note", throwable))
        );

    }

    @Override
    public void deleteNotesArray(ArrayList<Note> notes) {
        List<Integer> ids = new ArrayList<>();
        for (Note note : notes) ids.add(note.getId());
        getCompositeDisposable().add(
                getDataManager()
                        .moveNotesToTrash(ids)
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(
                                () -> {
                                }, throwable -> Log.e(TAG, "Error restoring notes", throwable))
        );
    }


    @Override
    public void noteMoveToTrash(Note note) {
        getCompositeDisposable().add(getDataManager().moveNoteToTrash(note.getId()).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui())
                .subscribe(() -> {
                        }, // onComplete
                        throwable -> Log.e(TAG, "Error deleting note", throwable)));
    }

    @Override
    public void restoreNoteLastMoveToTrash(Note nNote) {
        getCompositeDisposable().add(getDataManager()
                .transferNoteOutTrash(nNote.getId())
                .subscribeOn(getSchedulerProvider().io())
                .observeOn(getSchedulerProvider().ui())
                .subscribe(() -> {
                        }, // onComplete
                        throwable -> Log.e(TAG, "Error restoring note", throwable)));
    }

    private void performDelete(Tag tag) {
        getCompositeDisposable().add(
                getDataManager().getCountNotesTag(tag.getNameTag())
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(count -> {
                            if (count == 0) {
                                deleteTagFromDb(tag);
                            } else {
                                getView().startDeleteTagDialog(tag);
                            }
                        }, throwable -> Log.e(TAG, "count error", throwable))
        );
    }

    private void deleteTagFromDb(Tag tag) {
        getCompositeDisposable().add(
                getDataManager().deleteTag(tag)
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(
                                () -> { /* ок */ },
                                throwable -> Log.e(TAG, "Error deleting tag", throwable)
                        )
        );
    }

    @Override
    public void requestDeleteTag(Tag tag) {
        if (selectedTag.getValue() != null && selectedTag.getValue().getId() == tag.getId()) {
            selectedTag.onNext(SystemTagsManager.createAllNotesTag());
        }
        uiHandler.postDelayed(() -> performDelete(tag), 300);
    }


    @Override
    public void editVisibleTag(Tag tag) {
        getCompositeDisposable().add(getDataManager().updateTag(tag).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
                }, // onComplete
                throwable -> Log.e(TAG, "Error updating tag", throwable)));
    }

    public Note getBackupDeleteNote() {
        return backupDeleteNote;
    }

    public void setBackupDeleteNote(Note backupDeleteNote) {
        this.backupDeleteNote = backupDeleteNote;
    }

    @Override
    public void detachView() {
        super.detachView();
        backupDeleteNote = null;
    }

}
