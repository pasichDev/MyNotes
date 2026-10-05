package com.pasich.mynotes.utils.adapters;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import androidx.recyclerview.widget.RecyclerView;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.utils.adapters.notes.NoteAdapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** A drag shows its own order, and the dropped order lands without being dispatched twice. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NoteAdapterDragTest {

    private final List<String> events = new ArrayList<>();

    @Test
    public void aDropKeepsTheDraggedOrderWithoutMovingTheCardsAgain() throws Exception {
        NoteAdapter adapter = new NoteAdapter();
        adapter.registerAdapterDataObserver(
                new RecyclerView.AdapterDataObserver() {
                    @Override
                    public void onItemRangeMoved(int from, int to, int count) {
                        events.add("move " + from + "->" + to);
                    }

                    @Override
                    public void onItemRangeInserted(int start, int count) {
                        events.add("insert");
                    }

                    @Override
                    public void onItemRangeRemoved(int start, int count) {
                        events.add("remove");
                    }
                });
        settle(adapter, Arrays.asList(note(1), note(2), note(3)));
        events.clear();

        adapter.beginDrag();
        adapter.moveDuringDrag(2, 0);
        assertThat(ids(adapter.getCurrentList())).containsExactly(3, 1, 2).inOrder();
        assertThat(events).containsExactly("move 2->0");

        AtomicBoolean committed = new AtomicBoolean();
        adapter.endDrag(() -> committed.set(true));
        await(committed);

        assertThat(adapter.isDragging()).isFalse();
        assertThat(ids(adapter.getCurrentList())).containsExactly(3, 1, 2).inOrder();
        // Only the move made under the finger: the commit itself dispatched nothing.
        assertThat(events).containsExactly("move 2->0");
    }

    private void settle(NoteAdapter adapter, List<Note> list) throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        adapter.submitList(new ArrayList<>(list), () -> done.set(true));
        await(done);
    }

    private static void await(AtomicBoolean flag) throws Exception {
        for (int i = 0; i < 200 && !flag.get(); i++) {
            shadowOf(Looper.getMainLooper()).idle();
            if (!flag.get()) Thread.sleep(10);
        }
        assertThat(flag.get()).isTrue();
    }

    private static Note note(int id) {
        Note note = new Note().create("n" + id, "", id, "");
        note.setId(id);
        return note;
    }

    private static List<Integer> ids(List<Note> notes) {
        List<Integer> ids = new ArrayList<>();
        for (Note note : notes) ids.add(note.getId());
        return ids;
    }
}
