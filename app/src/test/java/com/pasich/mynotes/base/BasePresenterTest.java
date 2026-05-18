package com.pasich.mynotes.base;

import com.pasich.mynotes.utils.rx.SchedulerProvider;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import org.junit.Rule;
import org.mockito.MockitoAnnotations;

public abstract class BasePresenterTest {

    @Rule public RxImmediateSchedulerRule schedulers = new RxImmediateSchedulerRule();

    protected SchedulerProvider testSchedulerProvider() {
        return new SchedulerProvider() {
            @Override
            public Scheduler ui() {
                return Schedulers.trampoline();
            }

            @Override
            public Scheduler computation() {
                return Schedulers.trampoline();
            }

            @Override
            public Scheduler io() {
                return Schedulers.trampoline();
            }
        };
    }

    protected void initMocks(Object target) {
        MockitoAnnotations.openMocks(target);
    }
}
