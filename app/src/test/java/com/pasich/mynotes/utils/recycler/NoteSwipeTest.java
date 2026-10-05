package com.pasich.mynotes.utils.recycler;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Swipes a card to the left with real touch events: it is selected, not removed, and must come back
 * into its place although the list rebinds it in place (no change animations).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NoteSwipeTest {

    private RecyclerView list;
    private ItemTouchHelper helper;
    private int swipedLeft;
    private long downTime;

    private void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        list = new RecyclerView(activity);
        list.setLayoutManager(new NotesGridLayoutManager(1));
        list.setItemAnimator(new NotesItemAnimator(() -> true));
        list.setAdapter(new Cards());
        root.addView(list, new FrameLayout.LayoutParams(400, 800));
        activity.setContentView(root);
        helper =
                new ItemTouchHelper(
                        new NoteDragCallback(
                                new NoteDragCallback.Host() {
                                    @Override
                                    public boolean canDrag() {
                                        return false;
                                    }

                                    @Override
                                    public boolean canSwipe() {
                                        return true;
                                    }

                                    @Override
                                    public boolean canTrade(int first, int second) {
                                        return false;
                                    }

                                    @Override
                                    public boolean isDragging() {
                                        return false;
                                    }

                                    @Override
                                    public void move(int from, int to) {}

                                    @Override
                                    public void onDragStarted(
                                            @NonNull RecyclerView.ViewHolder holder) {}

                                    @Override
                                    public void onDragEnded(
                                            @NonNull RecyclerView.ViewHolder holder) {}

                                    @Override
                                    public void onSwiped(
                                            @NonNull RecyclerView.ViewHolder holder,
                                            int direction) {
                                        if (direction != ItemTouchHelper.LEFT) return;
                                        swipedLeft++;
                                        // What selecting does: the card is rebound in place.
                                        list.getAdapter()
                                                .notifyItemChanged(
                                                        holder.getBindingAdapterPosition(),
                                                        "selection");
                                        NoteDragCallback.returnSwipedCard(helper, holder);
                                    }
                                }));
        helper.attachToRecyclerView(list);
        idle(200);
    }

    /** Lets time pass, drawing the list every frame as the screen would. */
    private void idle(long ms) {
        Canvas canvas = new Canvas(Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888));
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
    public void aCardSwipedLeft_comesBackIntoPlace() {
        setUp();
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(1);
        float y = holder.itemView.getTop() + 50;
        touch(MotionEvent.ACTION_DOWN, 380, y);
        helper.startSwipe(holder);
        for (int i = 1; i <= 10; i++) {
            touch(MotionEvent.ACTION_MOVE, 380 - 36 * i, y);
            idle(20);
        }
        touch(MotionEvent.ACTION_UP, 20, y);
        idle(1000);

        assertThat(swipedLeft).isEqualTo(1);
        RecyclerView.ViewHolder shown = list.findViewHolderForAdapterPosition(1);
        assertThat(shown.itemView.getTranslationX()).isEqualTo(0f);
        assertThat(shown.itemView.getAlpha()).isEqualTo(1f);
    }

    private static final class Cards extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
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
            ((TextView) holder.itemView).setText(String.valueOf(position));
        }

        @Override
        public int getItemCount() {
            return 10;
        }
    }
}
