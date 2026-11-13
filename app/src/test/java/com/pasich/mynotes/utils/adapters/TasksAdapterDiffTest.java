package com.pasich.mynotes.utils.adapters;

import static com.google.common.truth.Truth.assertThat;

import androidx.recyclerview.widget.RecyclerView;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.utils.adapters.tasks.TasksAdapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** A database change rebinds only the rows it touched. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TasksAdapterDiffTest {

    private TasksAdapter adapter;
    private final List<String> events = new ArrayList<>();

    @Before
    public void setUp() {
        adapter = new TasksAdapter(t -> {}, t -> {}, t -> {}, () -> {}, h -> {}, t -> {});
        adapter.registerAdapterDataObserver(
                new RecyclerView.AdapterDataObserver() {
                    @Override
                    public void onChanged() {
                        events.add("all");
                    }

                    @Override
                    public void onItemRangeChanged(int start, int count, Object payload) {
                        events.add("changed " + start + "+" + count);
                    }

                    @Override
                    public void onItemRangeInserted(int start, int count) {
                        events.add("inserted " + start + "+" + count);
                    }

                    @Override
                    public void onItemRangeRemoved(int start, int count) {
                        events.add("removed " + start + "+" + count);
                    }

                    @Override
                    public void onItemRangeMoved(int from, int to, int count) {
                        events.add("moved " + from + ">" + to);
                    }
                });
    }

    private static Task task(int id, String title) {
        Task task = new Task(title, 0);
        task.setId(id);
        task.setCreatedAt(1L);
        return task;
    }

    @Test
    public void sameTasksAgain_nothingIsRebound() {
        adapter.submitList(Arrays.asList(task(1, "a"), task(2, "b")), Collections.emptyList());
        events.clear();

        adapter.submitList(Arrays.asList(task(1, "a"), task(2, "b")), Collections.emptyList());

        assertThat(events).isEmpty();
    }

    @Test
    public void oneTaskRenamed_onlyItsRowChanges() {
        adapter.submitList(Arrays.asList(task(1, "a"), task(2, "b")), Collections.emptyList());
        events.clear();

        adapter.submitList(Arrays.asList(task(1, "a"), task(2, "B")), Collections.emptyList());

        assertThat(events).containsExactly("changed 1+1");
    }

    @Test
    public void taskEditedInPlaceThenReloaded_rowIsRebound() {
        Task bound = task(1, "a");
        adapter.submitList(Collections.singletonList(bound), Collections.emptyList());
        events.clear();

        bound.setTitle("edited"); // what TasksPresenter.editTask does before writing
        adapter.submitList(Collections.singletonList(task(1, "edited")), Collections.emptyList());

        assertThat(events).containsExactly("changed 0+1");
    }
}
