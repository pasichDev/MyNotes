package com.pasich.mynotes.presenter;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.base.BasePresenterTest;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.contract.MainContract;
import com.pasich.mynotes.ui.presenter.MainPresenter;
import com.pasich.mynotes.ui.state.MainViewState;
import com.pasich.mynotes.utils.constants.settings.SortParam;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import com.pasich.mynotes.utils.search.SearchHit;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.processors.BehaviorProcessor;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.schedulers.TestScheduler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class MainPresenterTest extends BasePresenterTest {

    @Mock DataManager mockDataManager;

    @Mock MainContract.view mockView;

    private MainPresenter presenter;

    @Before
    public void setUp() {
        initMocks(this);

        // Mock all DataManager methods called during viewIsReady / initStreams / startStatsStream
        when(mockDataManager.getSortParam()).thenReturn("date");
        when(mockDataManager.getSortParamTags()).thenReturn("TagsCreationDateSort");
        when(mockDataManager.getTags()).thenReturn(Flowable.just(Collections.emptyList()));
        when(mockDataManager.getNotes()).thenReturn(Flowable.just(Collections.emptyList()));
        when(mockDataManager.getNotesCount()).thenReturn(Flowable.just(0));
        when(mockDataManager.getNotesCreatedLastMonth()).thenReturn(Flowable.just(0));
        when(mockDataManager.getTotalCharacters()).thenReturn(Flowable.just(0L));

        presenter =
                new MainPresenter(
                        testSchedulerProvider(), new CompositeDisposable(), mockDataManager);
    }

    @Test
    public void viewIsReady_callsSettingsListsAndListeners() {
        presenter.attachView(mockView);
        presenter.viewIsReady();

        verify(mockView).settingsLists();
        verify(mockView).initListeners();
    }

    @Test
    public void attachView_withEmptyNotes_doesNotCrash() {
        presenter.attachView(mockView);
        presenter.viewIsReady();

        // No NPE and render is called at least once via the state combiner
        verify(mockView, atLeastOnce()).render(org.mockito.Mockito.any());
    }

    @Test
    public void attachView_withNotes_rendersState() {
        Note note = new Note();
        List<Note> notes = Collections.singletonList(note);
        when(mockDataManager.getNotes()).thenReturn(Flowable.just(notes));

        // Re-create presenter with the updated mock
        presenter =
                new MainPresenter(
                        testSchedulerProvider(), new CompositeDisposable(), mockDataManager);

        presenter.attachView(mockView);
        presenter.viewIsReady();

        verify(mockView, atLeastOnce()).render(org.mockito.Mockito.any());
    }

    @Test
    public void detachView_afterAsyncCallback_doesNotThrowNPE() {
        presenter.attachView(mockView);
        presenter.viewIsReady();
        presenter.detachView();
        // If we get here without exception the test passes
    }

    // ---- Sync regressions (#170) -------------------------------------------------------------

    private final TestScheduler debounceClock = new TestScheduler();
    private BehaviorProcessor<List<Tag>> tagsDb;
    private BehaviorProcessor<List<Note>> notesDb;

    /** Presenter wired to processors standing in for Room, with a controllable debounce clock. */
    private MainPresenter syncPresenter(List<Tag> tags, List<Note> notes) {
        tagsDb = BehaviorProcessor.createDefault(tags);
        notesDb = BehaviorProcessor.createDefault(notes);
        when(mockDataManager.getTags()).thenReturn(tagsDb);
        when(mockDataManager.getNotes()).thenReturn(notesDb);
        when(mockDataManager.getSortParam()).thenReturn(SortParam.DataSort);
        SchedulerProvider provider =
                new SchedulerProvider() {
                    @Override
                    public Scheduler ui() {
                        return Schedulers.trampoline();
                    }

                    @Override
                    public Scheduler computation() {
                        return debounceClock;
                    }

                    @Override
                    public Scheduler io() {
                        return Schedulers.trampoline();
                    }
                };
        MainPresenter p = new MainPresenter(provider, new CompositeDisposable(), mockDataManager);
        p.attachView(mockView);
        p.viewIsReady();
        settle();
        return p;
    }

    private void settle() {
        debounceClock.advanceTimeBy(1, TimeUnit.SECONDS);
    }

    private MainViewState lastRendered() {
        ArgumentCaptor<MainViewState> captor = ArgumentCaptor.forClass(MainViewState.class);
        verify(mockView, atLeastOnce()).render(captor.capture());
        List<MainViewState> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }

    private static Tag userTag(long id, String name) {
        Tag t = new Tag().create(name);
        t.id = id;
        return t;
    }

    private static List<Tag> tagsWith(Tag... user) {
        List<Tag> list = new ArrayList<>(Arrays.asList(user));
        list.addAll(SystemTagsManager.getSystemTags());
        return list;
    }

    private static Note note(int id, String title, long date, String tag) {
        Note n = new Note().create(title, "body " + id, date, tag);
        n.setId(id);
        return n;
    }

    private static List<Integer> ids(MainViewState state) {
        List<Integer> ids = new ArrayList<>();
        for (Note n : state.notes()) ids.add(n.getId());
        return ids;
    }

    private static Tag selectedChip(MainViewState state) {
        for (Tag t : state.tags()) if (t.getSelected()) return t;
        return null;
    }

    @Test
    public void syncRenamesSelectedTag_categoryKeepsItsNotes() {
        MainPresenter p =
                syncPresenter(
                        tagsWith(userTag(5, "work")),
                        Arrays.asList(note(1, "a", 10, "work"), note(2, "b", 20, "")));
        p.onTagSelected(userTag(5, "work"));
        settle();
        assertThat(ids(lastRendered())).containsExactly(1);

        // A sync renames the tag on another device; the notes follow the new name.
        tagsDb.onNext(tagsWith(userTag(5, "job")));
        notesDb.onNext(Arrays.asList(note(1, "a", 10, "job"), note(2, "b", 20, "")));
        settle();

        MainViewState state = lastRendered();
        assertThat(state.selectedTag().getNameTag()).isEqualTo("job");
        assertThat(ids(state)).containsExactly(1);
        assertThat(selectedChip(state).getId()).isEqualTo(5L);
    }

    @Test
    public void syncDeletesSelectedTag_fallsBackToAllNotes() {
        MainPresenter p =
                syncPresenter(
                        tagsWith(userTag(5, "work")),
                        Arrays.asList(note(1, "a", 10, "work"), note(2, "b", 20, "")));
        p.onTagSelected(userTag(5, "work"));
        settle();

        tagsDb.onNext(tagsWith());
        notesDb.onNext(Arrays.asList(note(1, "a", 10, ""), note(2, "b", 20, "")));
        settle();

        MainViewState state = lastRendered();
        assertThat(SystemTagsManager.isAllNotesTag(state.selectedTag())).isTrue();
        assertThat(SystemTagsManager.isAllNotesTag(selectedChip(state))).isTrue();
        assertThat(ids(state)).containsExactly(2, 1).inOrder();
    }

    @Test
    public void syncReplacesSelectedTagWithSameName_keepsCategory() {
        MainPresenter p =
                syncPresenter(
                        tagsWith(userTag(5, "work")),
                        Arrays.asList(note(1, "a", 10, "work"), note(2, "b", 20, "")));
        p.onTagSelected(userTag(5, "work"));
        settle();

        // Dedupe on sync: local row 5 is dropped in favour of the remote row 9 with the same name.
        tagsDb.onNext(tagsWith(userTag(9, "work")));
        settle();

        MainViewState state = lastRendered();
        assertThat(state.selectedTag().getId()).isEqualTo(9L);
        assertThat(ids(state)).containsExactly(1);
        assertThat(selectedChip(state).getId()).isEqualTo(9L);
    }

    @Test
    public void syncInsertsUpdatesAndDeletes_listFollows() {
        syncPresenter(tagsWith(), Arrays.asList(note(1, "a", 10, ""), note(2, "b", 20, "")));
        assertThat(ids(lastRendered())).containsExactly(2, 1).inOrder();

        notesDb.onNext(
                Arrays.asList(note(1, "a", 10, ""), note(2, "b", 20, ""), note(3, "c", 30, "")));
        settle();
        assertThat(ids(lastRendered())).containsExactly(3, 2, 1).inOrder();

        notesDb.onNext(
                Arrays.asList(
                        note(1, "a edited", 40, ""), note(2, "b", 20, ""), note(3, "c", 30, "")));
        settle();
        MainViewState updated = lastRendered();
        assertThat(ids(updated)).containsExactly(1, 3, 2).inOrder();
        assertThat(updated.notes().get(0).getTitle()).isEqualTo("a edited");

        notesDb.onNext(Arrays.asList(note(1, "a edited", 40, ""), note(2, "b", 20, "")));
        settle();
        assertThat(ids(lastRendered())).containsExactly(1, 2).inOrder();
    }

    @Test
    public void identicalReEmission_doesNotRenderAgain() {
        syncPresenter(tagsWith(userTag(5, "work")), Arrays.asList(note(1, "a", 10, "work")));
        clearInvocations(mockView);

        // A no-op sync: Room re-emits fresh but equal rows.
        tagsDb.onNext(tagsWith(userTag(5, "work")));
        notesDb.onNext(Arrays.asList(note(1, "a", 10, "work")));
        settle();

        verify(mockView, never()).render(org.mockito.Mockito.any());
    }

    @Test
    public void tagsAndNotesEmittedTogether_renderOnce() {
        syncPresenter(tagsWith(), Collections.emptyList());
        clearInvocations(mockView);

        tagsDb.onNext(tagsWith(userTag(5, "work")));
        debounceClock.advanceTimeBy(5, TimeUnit.MILLISECONDS);
        notesDb.onNext(Arrays.asList(note(1, "a", 10, "work")));
        settle();

        verify(mockView, times(1)).render(org.mockito.Mockito.any());
        assertThat(ids(lastRendered())).containsExactly(1);
    }

    // ---- Search ranking (#175) ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<SearchHit> lastSearch(boolean expectTitlesOnly) {
        ArgumentCaptor<List<SearchHit>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockView, atLeastOnce()).renderSearch(captor.capture(), eq(expectTitlesOnly));
        List<List<SearchHit>> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }

    @Test
    public void search_ranksExactTitleFirstAfterDebounce() {
        MainPresenter p =
                syncPresenter(
                        tagsWith(),
                        Arrays.asList(
                                new Note().create("Weekly", "review the PR", 30, ""),
                                new Note().create("PR", "", 10, "")));
        clearInvocations(mockView);

        p.updateSearchQuery("PR ");
        verify(mockView, never()).renderSearch(any(), anyBoolean());
        settle();

        List<SearchHit> hits = lastSearch(false);
        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).note().getTitle()).isEqualTo("PR");
        assertThat(hits.get(0).kind()).isEqualTo(SearchHit.MatchKind.EXACT_TITLE);
    }

    @Test
    public void search_oneCharacterAsksToKeepTyping() {
        MainPresenter p =
                syncPresenter(
                        tagsWith(), Arrays.asList(new Note().create("Notes", "q is here", 30, "")));
        clearInvocations(mockView);

        p.updateSearchQuery("q");
        settle();

        assertThat(lastSearch(true)).isEmpty();
    }

    // ---- Custom order (#178) -----------------------------------------------------------------

    private static Note placed(int id, long position, boolean pinned) {
        Note n = note(id, "n" + id, id * 10L, "");
        n.setCustomPosition(position);
        n.setPinned(pinned);
        return n;
    }

    @Test
    public void customOrder_putsPinnedFirstThenTheUsersOrder() {
        when(mockDataManager.getSortParam()).thenReturn(SortParam.Custom);
        List<Note> notes =
                Arrays.asList(
                        placed(1, 3072, false),
                        placed(2, 1024, true),
                        placed(3, 5120, false),
                        placed(4, 4096, true),
                        placed(5, 2048, false));
        MainPresenter p = syncPresenter(tagsWith(), notes);
        p.onSortChanged(SortParam.Custom);
        settle();

        // Pinned notes keep their own section, each section in the user's order; the edit date
        // plays no part.
        assertThat(ids(lastRendered())).containsExactly(4, 2, 3, 1, 5).inOrder();
        assertThat(p.isCustomOrder()).isTrue();
    }

    @Test
    public void customOrder_switchingAwayAndBackKeepsTheOrder() {
        MainPresenter p =
                syncPresenter(
                        tagsWith(),
                        Arrays.asList(
                                placed(1, 3072, false),
                                placed(2, 1024, false),
                                placed(3, 2048, false)));
        p.onSortChanged(SortParam.Custom);
        settle();
        assertThat(ids(lastRendered())).containsExactly(1, 3, 2).inOrder();

        p.onSortChanged(SortParam.DataSort);
        settle();
        assertThat(ids(lastRendered())).containsExactly(3, 2, 1).inOrder();
        assertThat(p.isCustomOrder()).isFalse();

        p.onSortChanged(SortParam.Custom);
        settle();
        assertThat(ids(lastRendered())).containsExactly(1, 3, 2).inOrder();
    }

    @Test
    public void customOrder_equalPositionsFallBackToTheNewerNote() {
        assertThat(
                        MainPresenter.comparatorFor(SortParam.Custom)
                                .compare(placed(1, 1024, false), placed(2, 1024, false)))
                .isGreaterThan(0);
    }

    @Test
    public void moveNoteInCustomOrder_passesTheNeighboursToTheData() {
        when(mockDataManager.moveNoteInCustomOrder(7, 3, null))
                .thenReturn(io.reactivex.Completable.complete());
        MainPresenter p = syncPresenter(tagsWith(), Arrays.asList(placed(7, 1024, false)));

        p.moveNoteInCustomOrder(7, 3, null);

        verify(mockDataManager).moveNoteInCustomOrder(7, 3, null);
    }
}
