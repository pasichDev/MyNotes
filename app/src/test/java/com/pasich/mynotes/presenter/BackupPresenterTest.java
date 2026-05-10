package com.pasich.mynotes.presenter;

import static org.mockito.Mockito.verify;

import com.pasich.mynotes.base.BasePresenterTest;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.ui.contract.BackupContract;
import com.pasich.mynotes.ui.presenter.BackupPresenter;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import io.reactivex.disposables.CompositeDisposable;

public class BackupPresenterTest extends BasePresenterTest {

    @Mock
    DataManager mockDataManager;

    @Mock
    BackupContract.view mockView;

    private BackupPresenter presenter;

    @Before
    public void setUp() {
        initMocks(this);

        presenter = new BackupPresenter(
                testSchedulerProvider(),
                new CompositeDisposable(),
                mockDataManager
        );
        presenter.attachView(mockView);
    }

    @Test
    public void viewIsReady_callsInitActivity() {
        presenter.viewIsReady();

        verify(mockView).initActivity();
    }

    @Test
    public void viewIsReady_callsInitListeners() {
        presenter.viewIsReady();

        verify(mockView).initListeners();
    }

    @Test
    public void detachView_doesNotCrash() {
        presenter.viewIsReady();
        presenter.detachView();
        // If we get here without exception the test passes
    }
}
