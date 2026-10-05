package com.pasich.mynotes.ui.main;

import static com.google.common.truth.Truth.assertThat;

import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.utils.adapters.notes.NoteAdapter;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** The drag handle shows on cards that can be dragged, in the custom order only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DragHandleTest {

    private NoteAdapter.NoteHolder bound(NoteAdapter adapter, Note note) {
        FrameLayout parent =
                new FrameLayout(
                        new ContextThemeWrapper(
                                RuntimeEnvironment.getApplication(), R.style.DefaultTheme));
        RecyclerView list = new RecyclerView(parent.getContext());
        list.setLayoutManager(new LinearLayoutManager(parent.getContext()));
        parent.addView(list);
        adapter.setOnItemClickListener(null);
        adapter.submitList(List.of(note));
        ShadowLooper.idleMainLooper();
        NoteAdapter.NoteHolder holder = adapter.onCreateViewHolder(list, 0);
        adapter.onBindViewHolder(holder, 0);
        return holder;
    }

    private static Note note(int id, boolean pinned) {
        Note note = new Note().create("Title", "Text", 1L, "");
        note.setId(id);
        note.setPinned(pinned);
        return note;
    }

    @Test
    public void outsideTheCustomOrder_noHandle() {
        NoteAdapter adapter = new NoteAdapter();
        assertThat(
                        bound(adapter, note(1, false))
                                .itemView
                                .findViewById(R.id.dragHandle)
                                .getVisibility())
                .isEqualTo(View.GONE);
    }

    @Test
    public void inTheCustomOrder_unpinnedCardsHaveAHandle_pinnedOnesNot() {
        NoteAdapter adapter = new NoteAdapter();
        adapter.setReorderListener((n, step) -> {});
        assertThat(
                        bound(adapter, note(1, false))
                                .itemView
                                .findViewById(R.id.dragHandle)
                                .getVisibility())
                .isEqualTo(View.VISIBLE);

        NoteAdapter other = new NoteAdapter();
        other.setReorderListener((n, step) -> {});
        assertThat(
                        bound(other, note(2, true))
                                .itemView
                                .findViewById(R.id.dragHandle)
                                .getVisibility())
                .isEqualTo(View.GONE);
    }
}
