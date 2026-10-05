package com.pasich.mynotes.utils.recycler;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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
 * Holds a short card near the bottom of the visible list. As on the notes screen with the search
 * bar expanded, the list is taller than what is visible, so the card never crosses the list's own
 * bottom; it must scroll the list all the same.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NoteDragEdgeScrollTest {

    private static final int VISIBLE = 600;

    private RecyclerView list;
    private final List<Integer> ids = new ArrayList<>();
    private ItemTouchHelper helper;
    private boolean dragging;
    private long downTime;
    private final Canvas canvas =
            new Canvas(Bitmap.createBitmap(400, 1000, Bitmap.Config.ARGB_8888));

    private void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        FrameLayout window = new FrameLayout(activity);
        list = new RecyclerView(activity);
        list.setLayoutManager(new NotesGridLayoutManager(1));
        for (int i = 0; i < 30; i++) ids.add(i);
        list.setAdapter(new Cards());
        // The list reaches 300 px below what is visible, like under an expanded search bar.
        window.addView(list, new FrameLayout.LayoutParams(400, VISIBLE + 300));
        root.addView(window, new FrameLayout.LayoutParams(400, VISIBLE));
        activity.setContentView(root);
        NoteDragCallback callback =
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
                            }

                            @Override
                            public void onDragStarted(@NonNull RecyclerView.ViewHolder holder) {
                                dragging = true;
                            }

                            @Override
                            public void onDragEnded(@NonNull RecyclerView.ViewHolder holder) {
                                dragging = false;
                            }

                            @Override
                            public void onSwiped(
                                    @NonNull RecyclerView.ViewHolder holder, int direction) {}
                        });
        helper = new ItemTouchHelper(callback);
        callback.attachHelper(helper);
        helper.attachToRecyclerView(list);
        frames(200);
    }

    /** Lets time pass, drawing the list every frame as the screen would. */
    private void frames(long ms) {
        for (long t = 0; t < ms; t += 16) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
            list.draw(canvas);
        }
    }

    private void touch(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) downTime = now;
        MotionEvent event = MotionEvent.obtain(downTime, now, action, x, y, 0);
        list.dispatchTouchEvent(event);
        event.recycle();
    }

    @Test
    public void aShortCardHeldAtTheVisibleBottom_scrollsTheListAndMovesDown() {
        setUp();
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(1);
        View card = holder.itemView;
        float x = 200;
        float y = card.getTop() + card.getHeight() / 2f;
        touch(MotionEvent.ACTION_DOWN, x, y);
        helper.startDrag(holder);
        float to = VISIBLE - 20;
        for (int i = 1; i <= 10; i++) {
            touch(MotionEvent.ACTION_MOVE, x, y + (to - y) * i / 10);
            frames(16);
        }
        int movedBeforeHold = ids.indexOf(1);
        // Held still: only the edge scroll can move the list.
        frames(800);

        assertThat(list.computeVerticalScrollOffset()).isGreaterThan(0);
        assertThat(ids.indexOf(1)).isGreaterThan(movedBeforeHold);
        touch(MotionEvent.ACTION_UP, x, to);
        frames(400);
        assertThat(dragging).isFalse();
    }

    @Test
    public void aShortCardHeldInTheMiddle_doesNotScroll() {
        setUp();
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(1);
        View card = holder.itemView;
        float y = card.getTop() + card.getHeight() / 2f;
        touch(MotionEvent.ACTION_DOWN, 200, y);
        helper.startDrag(holder);
        for (int i = 1; i <= 10; i++) {
            touch(MotionEvent.ACTION_MOVE, 200, y + 15 * i);
            frames(16);
        }
        frames(500);

        assertThat(list.computeVerticalScrollOffset()).isEqualTo(0);
        touch(MotionEvent.ACTION_UP, 200, y + 150);
        frames(400);
    }

    private final class Cards extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            TextView view = new TextView(parent.getContext());
            view.setLayoutParams(
                    new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80));
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
