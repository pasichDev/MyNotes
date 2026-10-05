package com.pasich.mynotes.utils.recycler;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Drags a card with real touch events through {@link ItemTouchHelper}, {@link NoteDragCallback} and
 * {@link NotesGridLayoutManager}, in the list and in the grid, cards of different heights.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NoteDragTest {

    private RecyclerView list;
    private final List<Integer> ids = new ArrayList<>();
    private ItemTouchHelper helper;
    private boolean dragging;
    private int moves;
    private int ends;
    private long downTime;

    private void setUp(int spans) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        list = new RecyclerView(activity);
        list.setLayoutManager(new NotesGridLayoutManager(spans));
        for (int i = 0; i < 20; i++) ids.add(i);
        list.setAdapter(new Cards());
        root.addView(list, new FrameLayout.LayoutParams(400, 800));
        activity.setContentView(root);
        helper =
                new ItemTouchHelper(
                        new NoteDragCallback(
                                new NoteDragCallback.Host() {
                                    @Override
                                    public boolean canDrag() {
                                        return true;
                                    }

                                    @Override
                                    public boolean canSwipe() {
                                        return false;
                                    }

                                    @Override
                                    public boolean canTrade(int first, int second) {
                                        return first >= 0 && second >= 0;
                                    }

                                    @Override
                                    public boolean isDragging() {
                                        return dragging;
                                    }

                                    @Override
                                    public void move(int from, int to) {
                                        ids.add(to, ids.remove(from));
                                        list.getAdapter().notifyItemMoved(from, to);
                                        moves++;
                                    }

                                    @Override
                                    public void onDragStarted(
                                            @NonNull RecyclerView.ViewHolder holder) {
                                        dragging = true;
                                    }

                                    @Override
                                    public void onDragEnded(
                                            @NonNull RecyclerView.ViewHolder holder) {
                                        dragging = false;
                                        ends++;
                                    }

                                    @Override
                                    public void onSwiped(
                                            @NonNull RecyclerView.ViewHolder holder,
                                            int direction) {}
                                }) {
                            @Override
                            public int interpolateOutOfBoundsScroll(
                                    @NonNull RecyclerView recyclerView,
                                    int viewSize,
                                    int viewSizeOutOfBounds,
                                    int totalSize,
                                    long msSinceStartScroll) {
                                // No scrolling at the edges: under Robolectric's paused clock it
                                // never stops.
                                return 0;
                            }
                        });
        helper.attachToRecyclerView(list);
        idle(200);
    }

    private static void idle(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    private void touch(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) downTime = now;
        MotionEvent event = MotionEvent.obtain(downTime, now, action, x, y, 0);
        list.dispatchTouchEvent(event);
        event.recycle();
    }

    /** Picks the card up by its middle and moves the finger to (toX, toY), then lets go. */
    private void drag(int position, float toX, float toY) {
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
        View card = holder.itemView;
        float x = card.getLeft() + card.getWidth() / 2f;
        float y = card.getTop() + card.getHeight() / 2f;
        touch(MotionEvent.ACTION_DOWN, x, y);
        helper.startDrag(holder);
        int steps = 12;
        for (int i = 1; i <= steps; i++) {
            touch(MotionEvent.ACTION_MOVE, x + (toX - x) * i / steps, y + (toY - y) * i / steps);
            idle(20);
            // Still held: the drag must not have been dropped on the way.
            assertThat(ends).isEqualTo(0);
        }
        touch(MotionEvent.ACTION_UP, toX, toY);
        idle(600);
    }

    private View shown(int id) {
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (((TextView) child).getText().toString().equals(String.valueOf(id))) return child;
        }
        return null;
    }

    private void assertFullyShown(int id) {
        View card = shown(id);
        assertThat(card).isNotNull();
        assertThat(card.getTop()).isAtLeast(0);
        assertThat(card.getBottom()).isAtMost(list.getHeight());
    }

    @Test
    public void inTheList_aCardDraggedOntoTheFirstOne_becomesFirstAndStaysOnScreen() {
        setUp(1);
        drag(3, 200, 30);

        assertThat(ids.subList(0, 4)).containsExactly(3, 0, 1, 2).inOrder();
        assertThat(ends).isEqualTo(1);
        assertFullyShown(3);
        assertThat(shown(3).getTop()).isEqualTo(0);
    }

    @Test
    public void inTheGrid_aCardDraggedToTheTop_becomesFirstAndStaysOnScreen() {
        setUp(2);
        drag(5, 100, 30);

        assertThat(ids.get(0)).isEqualTo(5);
        assertThat(ends).isEqualTo(1);
        assertFullyShown(5);
    }

    @Test
    public void inTheList_aCardDraggedDown_landsWhereItWasLetGo() {
        setUp(1);
        drag(0, 200, 470);

        assertThat(moves).isGreaterThan(0);
        assertThat(ids.indexOf(0)).isGreaterThan(0);
        assertFullyShown(0);
    }

    private final class Cards extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            TextView view = new TextView(parent.getContext());
            view.setLayoutParams(
                    new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100));
            return new RecyclerView.ViewHolder(view) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int id = ids.get(position);
            holder.itemView.getLayoutParams().height = 100 + (id % 3) * 60;
            ((TextView) holder.itemView).setText(String.valueOf(id));
        }

        @Override
        public int getItemCount() {
            return ids.size();
        }
    }
}
