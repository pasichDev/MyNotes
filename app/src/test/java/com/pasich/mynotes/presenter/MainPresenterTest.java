package com.pasich.mynotes.presenter;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.base.BasePresenterTest;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.contract.MainContract;
import com.pasich.mynotes.ui.presenter.MainPresenter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;

public class MainPresenterTest extends BasePresenterTest {

    @Mock
    DataManager mockDataManager;

    @Mock
    MainContract.view mockView;

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

        presenter = new MainPresenter(
                testSchedulerProvider(),
                new CompositeDisposable(),
                mockDataManager
        );
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
        presenter = new MainPresenter(
                testSchedulerProvider(),
                new CompositeDisposable(),
                mockDataManager
        );

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
}
