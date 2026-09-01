package com.pasich.mynotes.presenter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.base.BasePresenterTest;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.ui.contract.dialogs.MoreNoteDialogContract;
import com.pasich.mynotes.ui.presenter.dialogs.MoreNoteDialogPresenter;
import io.reactivex.disposables.CompositeDisposable;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class MoreNoteDialogPresenterTest extends BasePresenterTest {

    @Mock DataManager dataManager;
    @Mock MoreNoteDialogContract.view view;

    private MoreNoteDialogPresenter presenter;

    @Before
    public void setUp() {
        initMocks(this);
        presenter =
                new MoreNoteDialogPresenter(
                        testSchedulerProvider(), new CompositeDisposable(), dataManager);
        presenter.attachView(view);
    }

    @Test
    public void viewIsReady_textSizeBelowSliderRange_usesMinimumSliderValue() {
        when(dataManager.getSizeTextNoteActivity()).thenReturn(0);

        presenter.viewIsReady();

        verify(view).setSliderValue(10);
    }

    @Test
    public void viewIsReady_textSizeAboveSliderRange_usesMaximumSliderValue() {
        when(dataManager.getSizeTextNoteActivity()).thenReturn(40);

        presenter.viewIsReady();

        verify(view).setSliderValue(32);
    }
}
