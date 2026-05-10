package com.pasich.mynotes.presenter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.base.BasePresenterTest;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.ui.contract.TrashContract;
import com.pasich.mynotes.ui.presenter.TrashPresenter;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;

public class TrashPresenterTest extends BasePresenterTest {

    @Mock
    DataManager mockDataManager;

    @Mock
    TrashContract.view mockView;

    private TrashPresenter presenter;

    @Before
    public void setUp() {
        initMocks(this);

        // Default stub for loadingTrash() called inside viewIsReady()
        when(mockDataManager.getNotesInTrash())
                .thenReturn(Flowable.just(Collections.emptyList()));

        presenter = new TrashPresenter(
                testSchedulerProvider(),
                new CompositeDisposable(),
                mockDataManager
        );
        presenter.attachView(mockView);
    }

    @Test
    public void loadingTrash_callsRenderOnView() {
        presenter.viewIsReady();

        verify(mockView).initListeners();
    }

    @Test
    public void clearTrash_completesSuccessfully_callsLoadDataWithEmptyList() {
        when(mockDataManager.clearTrash()).thenReturn(Completable.complete());

        presenter.clearTrash();

        // clearTrash() calls view.loadData(new ArrayList<>()) on success
        verify(mockView).loadData(any(List.class));
    }

    @Test
    public void clearTrash_onError_doesNotCrash() {
        when(mockDataManager.clearTrash())
                .thenReturn(Completable.error(new RuntimeException("DB error")));

        // Should not throw
        presenter.clearTrash();
    }

    @Test
    public void restoreNotesArray_callsRestoreNotesAndFixTags() {
        when(mockDataManager.restoreNotesAndFixTags(any()))
                .thenReturn(Completable.complete());

        Note note = new Note();
        note.setId(1);
        ArrayList<Note> notes = new ArrayList<>();
        notes.add(note);

        presenter.restoreNotesArray(notes);

        verify(mockDataManager).restoreNotesAndFixTags(any());
    }
}
