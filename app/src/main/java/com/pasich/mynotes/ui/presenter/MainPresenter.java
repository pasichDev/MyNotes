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
import com.pasich.mynotes.ui.state.StatsData;
import com.pasich.mynotes.ui.state.UiEvent;
import com.pasich.mynotes.utils.TagsSorter;
import com.pasich.mynotes.utils.constants.settings.SortParam;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import com.pasich.mynotes.utils.search.NoteSearchRanker;
import com.pasich.mynotes.utils.search.SearchHit;
import dagger.hilt.android.scopes.ActivityScoped;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.BehaviorSubject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/** Presenter backing the main notes list screen. */
@ActivityScoped
public class MainPresenter extends BasePresenter<MainContract.view>
        implements MainContract.presenter {

    private static final String TAG = "MainPresenter";

    /**
     * Room emits tags and notes separately, so one sync or edit can arrive as two or more emissions
     * a few milliseconds apart. Waiting this long folds them into a single render.
     */
    static final long STATE_DEBOUNCE_MS = 50;

    /** Folds the burst of a note edit and a query change into one ranking pass. */
    static final long SEARCH_DEBOUNCE_MS = 150;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final BehaviorSubject<Tag> selectedTag =
            BehaviorSubject.createDefault(SystemTagsManager.createAllNotesTag());
    private final BehaviorSubject<String> sortParam = BehaviorSubject.create();
    private final BehaviorSubject<MainViewState> viewState = BehaviorSubject.create();
    private final BehaviorSubject<String> searchQuery = BehaviorSubject.createDefault("");
    private final BehaviorSubject<String> searchTagFilter = BehaviorSubject.createDefault("");

    private final NoteSearchRanker searchRanker = new NoteSearchRanker();

    private UiEvent lastUiEvent = UiEvent.NONE;
    private Note backupDeleteNote;
    private Observable<List<Tag>> tagsStream;
    private Observable<List<Note>> notesStream;

    @Inject
    public MainPresenter(
            SchedulerProvider schedulerProvider,
            CompositeDisposable compositeDisposable,
            DataManager dataManager) {
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
        startStatsStream();
    }

    void startStatsStream() {
        getCompositeDisposable()
                .add(
                        Flowable.combineLatest(
                                        getDataManager().getNotesCount(),
                                        getDataManager().getNotesCreatedLastMonth(),
                                        getDataManager().getTotalCharacters(),
                                        StatsData::new)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        stats -> getView().renderDrawerStats(stats),
                                        error -> Log.e(TAG, "drawer stats stream error", error)));
    }

    void starListsRenderStream() {
        getCompositeDisposable()
                .add(
                        viewState
                                .hide()
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        state -> getView().render(state),
                                        throwable -> Log.e(TAG, "observeState", throwable)));
    }

    private void initStreams() {
        tagsStream =
                getDataManager()
                        .getTags()
                        .toObservable()
                        .map(
                                tagList ->
                                        TagsSorter.sortTags(
                                                tagList, getDataManager().getSortParamTags()));

        notesStream = getDataManager().getNotes().toObservable();
    }

    private void startStateCombiner() {
        getCompositeDisposable()
                .add(
                        Observable.combineLatest(
                                        tagsStream,
                                        notesStream,
                                        selectedTag,
                                        sortParam,
                                        this::buildState)
                                .debounce(
                                        STATE_DEBOUNCE_MS,
                                        TimeUnit.MILLISECONDS,
                                        getSchedulerProvider().computation())
                                .distinctUntilChanged()
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        state -> getView().render(state),
                                        throwable -> Log.e(TAG, "combineLatest", throwable)));
    }

    private MainViewState buildState(
            List<Tag> sortedTags, List<Note> notes, Tag requestedTag, String sort) {

        Tag selectedTag = resolveSelectedTag(sortedTags, requestedTag);
        if (!sameSelection(selectedTag, requestedTag)) {
            // The tag was renamed, merged or deleted by a sync: remember the fresh one so new
            // notes, the next state and the chips all agree with what is on screen.
            this.selectedTag.onNext(selectedTag);
        }

        List<Tag> tags = new ArrayList<>(sortedTags.size());
        for (Tag t : sortedTags) {
            Tag copy = t.copy();
            copy.setSelected(t.getId() == selectedTag.getId());
            tags.add(copy);
        }

        Set<String> hidden = new HashSet<>();
        for (Tag t : tags) {
            if (t.getVisibility() == 1) {
                hidden.add(t.getNameTag());
            }
        }

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

        boolean isAllNotes = SystemTagsManager.isAllNotesTag(selectedTag);

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

        List<Note> sorted = new ArrayList<>(filtered);

        // pinned always first, then by date
        boolean sortByNew = SortParam.DataSort.equals(sort);
        sorted.sort(
                (a, b) -> {
                    if (a.isPinned() != b.isPinned()) return a.isPinned() ? -1 : 1;
                    return sortByNew
                            ? Long.compare(b.getDate(), a.getDate())
                            : Long.compare(a.getDate(), b.getDate());
                });

        return new MainViewState(tags, sorted, selectedTag, lastUiEvent);
    }

    /**
     * Finds the selected category in a fresh tags list: by id first (survives a rename), then by
     * name (survives a sync that replaced the row with an equal one), otherwise "All notes".
     */
    static Tag resolveSelectedTag(List<Tag> tags, Tag requested) {
        Tag allNotes = null;
        for (Tag t : tags) {
            if (SystemTagsManager.isAllNotesTag(t)) {
                allNotes = t;
                break;
            }
        }
        if (allNotes == null) allNotes = SystemTagsManager.createAllNotesTag();

        if (requested == null || SystemTagsManager.isSystemTag(requested)) return allNotes;

        for (Tag t : tags) {
            if (!SystemTagsManager.isSystemTag(t) && t.getId() == requested.getId()) return t;
        }
        String name = requested.getNameTag();
        for (Tag t : tags) {
            if (!SystemTagsManager.isSystemTag(t) && t.getNameTag().equals(name)) return t;
        }
        return allNotes;
    }

    private static boolean sameSelection(Tag a, Tag b) {
        if (a == null || b == null) return a == b;
        return a.getId() == b.getId()
                && a.getSystemAction() == b.getSystemAction()
                && a.getNameTag().equals(b.getNameTag());
    }

    private void startSearchStream() {
        getCompositeDisposable()
                .add(
                        Observable.combineLatest(
                                        notesStream,
                                        searchQuery,
                                        searchTagFilter,
                                        SearchRequest::new)
                                .debounce(
                                        SEARCH_DEBOUNCE_MS,
                                        TimeUnit.MILLISECONDS,
                                        getSchedulerProvider().computation())
                                .map(this::runSearch)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        result ->
                                                getView()
                                                        .renderSearch(
                                                                result.hits, result.titlesOnly),
                                        throwable -> Log.e(TAG, "search error", throwable)));
    }

    /** Ranks off the main thread: the debounce hands the latest request to a worker. */
    private SearchResult runSearch(SearchRequest request) {
        List<SearchHit> hits = searchRanker.rank(request.notes, request.query, request.tagFilter);
        return new SearchResult(hits, NoteSearchRanker.searchesTitlesOnly(request.query));
    }

    private static final class SearchRequest {
        final List<Note> notes;
        final String query;
        final String tagFilter;

        SearchRequest(List<Note> notes, String query, String tagFilter) {
            this.notes = notes;
            this.query = query;
            this.tagFilter = tagFilter;
        }
    }

    private static final class SearchResult {
        final List<SearchHit> hits;
        final boolean titlesOnly;

        SearchResult(List<SearchHit> hits, boolean titlesOnly) {
            this.hits = hits;
            this.titlesOnly = titlesOnly;
        }
    }

    public void updateSearchQuery(String query) {
        searchQuery.onNext(query);
    }

    @Override
    public void updateSearchTagFilter(String tagName) {
        searchTagFilter.onNext(tagName != null ? tagName : "");
    }

    @Override
    public void onSortChanged(String newSort) {
        if (newSort == null || newSort.equals(sortParam.getValue())) return;
        lastUiEvent = UiEvent.SORT_CHANGED;
        sortParam.onNext(newSort);
    }

    @Override
    public void onTagSelected(Tag tag) {
        if (tag == null || sameSelection(tag, selectedTag.getValue())) return;
        lastUiEvent = UiEvent.TAG_CHANGED;
        selectedTag.onNext(tag);
    }

    @Override
    public void requestTagSelection(boolean multiple) {
        getCompositeDisposable()
                .add(
                        tagsStream
                                .take(1)
                                .map(
                                        tags -> {
                                            List<Tag> filtered = new ArrayList<>();
                                            for (Tag t : tags) {
                                                if (!SystemTagsManager.isSystemTag(t)) {
                                                    filtered.add(t);
                                                }
                                            }
                                            return filtered;
                                        })
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        tags -> {
                                            if (getView() == null) return;

                                            if (multiple) {
                                                getView().multipleTagChangerDialog(tags);
                                            } else {
                                                getView().allTagSelectDialog(tags);
                                            }
                                        },
                                        err -> Log.e(TAG, "requestTagSelection()", err)));
    }

    @Override
    public void requestTagChangeMultipleNotes(String selectedTag, List<Integer> notesIds) {
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .setTagForNotes(selectedTag, notesIds)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        () -> {},
                                        throwable ->
                                                Log.e(TAG, "Error restoring notes", throwable)));
    }

    @Override
    public void clearUiEvent() {
        lastUiEvent = UiEvent.NONE;
    }

    @Override
    public void newNotesClick() {
        if (!isViewAttached()) return;

        String tag = "";
        Tag current = selectedTag.getValue();

        // If the non-system tag ALL_NOTES is selected → assign the tag
        if (current != null
                && current.getSystemAction() != SystemTagsManager.SYSTEM_ACTION_ALL_NOTES) {
            tag = current.getNameTag();
        }

        Note newNote = new Note().create("", "", System.currentTimeMillis(), tag);
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .addNote(newNote)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        id -> {
                                            lastUiEvent = UiEvent.NOTE_CREATED;
                                            uiHandler.postDelayed(
                                                    () -> {
                                                        if (isViewAttached())
                                                            getView().openNewNoteWithId(id);
                                                    },
                                                    80);
                                        },
                                        throwable ->
                                                Log.e(TAG, "Failed to create note", throwable)));
    }

    @Override
    public void deleteNotesArray(ArrayList<Note> notes) {
        List<Integer> ids = new ArrayList<>();
        for (Note note : notes) ids.add(note.getId());
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .moveNotesToTrash(ids)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        () -> {},
                                        throwable ->
                                                Log.e(TAG, "Error restoring notes", throwable)));
    }

    @Override
    public void noteMoveToTrash(Note note) {
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .moveNoteToTrash(note.getId())
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        () -> {}, // onComplete
                                        throwable -> Log.e(TAG, "Error deleting note", throwable)));
    }

    @Override
    public void restoreNoteLastMoveToTrash(Note nNote) {
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .transferNoteOutTrash(nNote.getId())
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        () -> {}, // onComplete
                                        throwable ->
                                                Log.e(TAG, "Error restoring note", throwable)));
    }

    private void performDelete(Tag tag) {
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .getCountNotesTag(tag.getNameTag())
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        count -> {
                                            if (count == 0) {
                                                deleteTagFromDb(tag);
                                            } else {
                                                getView().startDeleteTagDialog(tag);
                                            }
                                        },
                                        throwable -> Log.e(TAG, "count error", throwable)));
    }

    private void deleteTagFromDb(Tag tag) {
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .deleteTag(tag)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        () -> {},
                                        throwable -> Log.e(TAG, "Error deleting tag", throwable)));
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
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .updateTag(tag)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        () -> {}, // onComplete
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
        uiHandler.removeCallbacksAndMessages(null);
        super.detachView();
        backupDeleteNote = null;
    }
}
