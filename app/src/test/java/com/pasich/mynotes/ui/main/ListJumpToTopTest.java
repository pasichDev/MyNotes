package com.pasich.mynotes.ui.main;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.pasich.mynotes.ui.controllers.mainActivity.MainRenderListsController;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * "Show the new top note" after a list change, on a real {@link StaggeredGridLayoutManager} that
 * lays out real views. A scroll requested in the same layout pass as the change it reacts to is
 * resolved by the layout manager against the children of the old list: a removed first card made it
 * lay out nothing at all, a moved card made it land at a wrong offset.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ListJumpToTopTest {

    private static final int ITEM_HEIGHT = 120;

    private RecyclerView list;
    private Items adapter;
    private MainRenderListsController controller;

    private void setUp(int spans) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        list = new RecyclerView(activity);
        list.setLayoutManager(
                new StaggeredGridLayoutManager(spans, StaggeredGridLayoutManager.VERTICAL));
        adapter = new Items(30);
        list.setAdapter(adapter);
        root.addView(list, new FrameLayout.LayoutParams(400, 600));
        View empty = new FrameLayout(activity);
        View tags = new FrameLayout(activity);
        root.addView(empty);
        root.addView(tags);
        activity.setContentView(root);
        controller =
                new MainRenderListsController(
                        list,
                        empty,
                        new TextView(activity),
                        new ImageView(activity),
                        tags,
                        () -> false);
        settle();
    }

    private static void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
    }

    private void scrollDownBy(int items) {
        list.scrollBy(0, items * ITEM_HEIGHT);
        settle();
    }

    private void assertTopNoteFullyVisible(int expectedId) {
        RecyclerView.ViewHolder top = list.findViewHolderForAdapterPosition(0);
        assertThat(list.getChildCount()).isGreaterThan(0);
        assertThat(top).isNotNull();
        assertThat(adapter.ids.get(0)).isEqualTo(expectedId);
        assertThat(((TextView) top.itemView).getText().toString())
                .isEqualTo(String.valueOf(expectedId));
        assertThat(top.itemView.getTop()).isEqualTo(list.getPaddingTop());
    }

    @Test
    public void removingTheTopNote_list_keepsTheNotesOnScreen() {
        setUp(1);
        adapter.remove(0);
        controller.jumpToTop();
        settle();

        assertTopNoteFullyVisible(1);
    }

    @Test
    public void removingTheTopNote_grid_keepsTheNotesOnScreen() {
        setUp(2);
        adapter.remove(0);
        controller.prepareSwappedList(adapter.getItemCount(), true);
        settle();

        assertTopNoteFullyVisible(1);
    }

    @Test
    public void aVisibleNoteMovedToTheTop_list_isShownAtTheTop() {
        setUp(1);
        scrollDownBy(2);
        adapter.move(4, 0);
        controller.jumpToTop();
        settle();

        assertTopNoteFullyVisible(4);
    }

    @Test
    public void aVisibleNoteMovedToTheTop_grid_isShownAtTheTop() {
        setUp(2);
        scrollDownBy(2);
        adapter.move(7, 0);
        controller.prepareSwappedList(adapter.getItemCount(), true);
        settle();

        assertTopNoteFullyVisible(7);
    }

    @Test
    public void aNewNoteAtTheTop_whileScrolledDown_isShownAtTheTop() {
        setUp(2);
        scrollDownBy(3);
        adapter.insertTop(100);
        controller.prepareSwappedList(adapter.getItemCount(), true);
        settle();

        assertTopNoteFullyVisible(100);
    }

    @Test
    public void jumpWithNothingPending_isAppliedRightAway() {
        setUp(2);
        scrollDownBy(3);
        controller.jumpToTop();
        settle();

        assertTopNoteFullyVisible(0);
    }

    private static final class Items extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final List<Integer> ids = new ArrayList<>();

        Items(int count) {
            for (int i = 0; i < count; i++) ids.add(i);
        }

        void remove(int position) {
            ids.remove(position);
            notifyItemRemoved(position);
        }

        void move(int from, int to) {
            ids.add(to, ids.remove(from));
            notifyItemMoved(from, to);
        }

        void insertTop(int id) {
            ids.add(0, id);
            notifyItemInserted(0);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            TextView view = new TextView(parent.getContext());
            view.setLayoutParams(
                    new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ITEM_HEIGHT));
            return new RecyclerView.ViewHolder(view) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((TextView) holder.itemView).setText(String.valueOf(ids.get(position)));
        }

        @Override
        public int getItemCount() {
            return ids.size();
        }
    }
}
