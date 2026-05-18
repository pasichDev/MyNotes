package com.pasich.mynotes.presenter;

import static com.google.common.truth.Truth.assertThat;

import com.pasich.mynotes.base.BasePresenterTest;
import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.data.DataManager;
import io.reactivex.disposables.CompositeDisposable;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class BasePresenterSafetyTest extends BasePresenterTest {

    static class TestPresenter extends BasePresenter<BaseView> {
        boolean actionWasCalled = false;

        TestPresenter(
                com.pasich.mynotes.utils.rx.SchedulerProvider sp,
                CompositeDisposable cd,
                DataManager dm) {
            super(sp, cd, dm);
        }

        @Override
        public void viewIsReady() {}

        public void triggerViewAction() {
            runOnView(view -> actionWasCalled = true);
        }
    }

    @Mock DataManager mockDataManager;
    TestPresenter presenter;

    @Before
    public void setUp() {
        initMocks(this);
        presenter =
                new TestPresenter(
                        testSchedulerProvider(), new CompositeDisposable(), mockDataManager);
    }

    @Test
    public void runOnView_whenViewAttached_executesAction() {
        BaseView mockView = org.mockito.Mockito.mock(BaseView.class);
        presenter.attachView(mockView);

        presenter.triggerViewAction();

        assertThat(presenter.actionWasCalled).isTrue();
    }

    @Test
    public void runOnView_whenViewDetached_doesNotThrowNPE() {
        BaseView mockView = org.mockito.Mockito.mock(BaseView.class);
        presenter.attachView(mockView);
        presenter.detachView();

        presenter.triggerViewAction();

        assertThat(presenter.actionWasCalled).isFalse();
    }

    @Test
    public void runOnView_whenViewNeverAttached_doesNotThrowNPE() {
        presenter.triggerViewAction();

        assertThat(presenter.actionWasCalled).isFalse();
    }
}
