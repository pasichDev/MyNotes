package com.pasich.mynotes.presenter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import com.pasich.mynotes.base.BasePresenterTest;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.ui.contract.TasksContract;
import com.pasich.mynotes.ui.presenter.TasksPresenter;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.TestScheduler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class TasksPresenterTest extends BasePresenterTest {

    @Mock DataManager dataManager;
    @Mock TasksContract.view view;

    private final TestScheduler io = new TestScheduler();
    private TasksPresenter presenter;

    @Before
    public void setUp() {
        initMocks(this);
        when(dataManager.getCategories()).thenReturn(Flowable.just(Collections.emptyList()));
        when(dataManager.getActiveTasks()).thenReturn(Flowable.just(Collections.emptyList()));
        when(dataManager.getActiveTasksByCategory(anyInt()))
                .thenReturn(Flowable.just(Collections.emptyList()));
        when(dataManager.getCompletedTasks()).thenReturn(Flowable.just(Collections.emptyList()));
        SchedulerProvider immediate = testSchedulerProvider();
        SchedulerProvider queued =
                new SchedulerProvider() {
                    @Override
                    public Scheduler ui() {
                        return immediate.ui();
                    }

                    @Override
                    public Scheduler computation() {
                        return immediate.computation();
                    }

                    @Override
                    public Scheduler io() {
                        return io;
                    }
                };
        presenter =
                new TasksPresenter(
                        queued, new CompositeDisposable(), dataManager, mock(Context.class));
        presenter.attachView(view);
        presenter.viewIsReady();
        io.triggerActions();
    }

    private static Task task(int id, int position) {
        Task task = new Task("T" + id, 0);
        task.setId(id);
        task.setPosition(position);
        return task;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void updatePositions_writesOnlyTheMovedTasks_inOneCall() {
        when(dataManager.updateTasks(anyList())).thenReturn(Completable.complete());
        // Task 3 dragged to the top: all three moved.
        List<Task> order = new ArrayList<>(Arrays.asList(task(3, 2), task(1, 0), task(2, 1)));

        presenter.updatePositions(order);
        io.triggerActions();

        ArgumentCaptor<List<Task>> written = ArgumentCaptor.forClass(List.class);
        verify(dataManager).updateTasks(written.capture());
        assertEquals(3, written.getValue().size());
        verify(dataManager, never()).updateTask(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void updatePositions_nothingMoved_writesNothing() {
        presenter.updatePositions(Arrays.asList(task(1, 0), task(2, 1)));

        verify(dataManager, never()).updateTasks(anyList());
    }

    @Test
    public void switchingCategory_doesNotCancelAWriteInFlight() {
        AtomicBoolean ran = new AtomicBoolean();
        when(dataManager.updateTask(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Completable.fromAction(() -> ran.set(true)));

        presenter.editTask(task(1, 0), "New title", null);
        presenter.onCategorySelected(5); // used to clear every subscription
        io.triggerActions();

        assertTrue(ran.get());
    }
}
