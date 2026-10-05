package com.pasich.mynotes.ui.main;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.controllers.mainActivity.MainRenderListsController;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainRenderListsControllerTest {

    private RecyclerView list;
    private View empty;
    private View tags;
    private final AtomicBoolean animationsOn = new AtomicBoolean(true);
    private MainRenderListsController controller;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        list = new RecyclerView(activity);
        list.setLayoutManager(
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        empty = new FrameLayout(activity);
        empty.setVisibility(View.GONE);
        tags = new FrameLayout(activity);
        tags.setVisibility(View.GONE);
        root.addView(list);
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
                        animationsOn::get);
    }

    private static void runAnimations() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
    }

    @Test
    public void emptyThenNotesWithinTheHideAnimation_listEndsVisible() {
        controller.showStateNoteList(null, 0, true);
        // Sync fills the list again before the 160 ms hide finishes.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40));
        controller.showStateNoteList(null, 5, true);
        runAnimations();

        assertThat(list.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(list.getAlpha()).isEqualTo(1f);
        assertThat(empty.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void notesThenEmpty_emptyStateEndsVisible() {
        controller.showStateNoteList(null, 5, true);
        controller.showStateNoteList(null, 0, true);
        runAnimations();

        assertThat(list.getVisibility()).isEqualTo(View.INVISIBLE);
        assertThat(empty.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(empty.getAlpha()).isEqualTo(1f);
    }

    @Test
    public void repeatedRequests_areIdempotent() {
        controller.showStateNoteList(null, 0, true);
        controller.showStateNoteList(null, 0, true);
        runAnimations();
        controller.showStateNoteList(null, 3, true);
        controller.showStateNoteList(null, 3, true);
        runAnimations();

        assertThat(list.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(list.getAlpha()).isEqualTo(1f);
        assertThat(list.getScaleY()).isEqualTo(1f);
        assertThat(empty.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void animationsOff_appliesImmediately() {
        animationsOn.set(false);
        controller.showStateNoteList(null, 0, true);

        assertThat(list.getVisibility()).isEqualTo(View.INVISIBLE);
        assertThat(empty.getVisibility()).isEqualTo(View.VISIBLE);

        controller.showStateNoteList(null, 2, true);
        assertThat(list.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(list.getAlpha()).isEqualTo(1f);
        assertThat(empty.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void swap_submitsWhileHiddenAndRevealsAfterLayout() {
        AtomicInteger submits = new AtomicInteger();
        controller.swapListContent(true, submits::incrementAndGet);
        assertThat(controller.isSwapping()).isTrue();
        // A newer swap during the fade replaces the older one: only the latest list is submitted.
        controller.swapListContent(true, submits::incrementAndGet);
        runAnimations();
        assertThat(submits.get()).isEqualTo(1);
        assertThat(list.getAlpha()).isEqualTo(0f);

        controller.showStateNoteList(null, 4, true);
        list.requestLayout();
        runAnimations();

        assertThat(controller.isSwapping()).isFalse();
        assertThat(list.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(list.getAlpha()).isEqualTo(1f);
    }

    @Test
    public void swapWithAnimationsOff_submitsAtOnce() {
        animationsOn.set(false);
        AtomicInteger submits = new AtomicInteger();
        controller.swapListContent(true, submits::incrementAndGet);
        assertThat(submits.get()).isEqualTo(1);
        assertThat(controller.isSwapping()).isFalse();
    }

    @Test
    public void tagsRowCollapseThenExpand_endsVisible() {
        controller.renderListTags(List.of(new Tag().create("work")), true);
        runAnimations();
        controller.renderListTags(List.<Tag>of(), true);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30));
        controller.renderListTags(List.of(new Tag().create("work")), true);
        runAnimations();

        assertThat(tags.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(tags.getAlpha()).isEqualTo(1f);
    }

    @Test
    public void swappingInAnEmptyList_leavesTheGridAlone() {
        // Resetting the spans of a StaggeredGridLayoutManager or scrolling it while it has no
        // items left it anchored wrong on a device: the next list was laid out below the screen.
        StaggeredGridLayoutManager grid =
                Mockito.spy(new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL));
        list.setLayoutManager(grid);
        Mockito.clearInvocations(grid);

        controller.prepareSwappedList(0, true);

        Mockito.verify(grid, Mockito.never()).invalidateSpanAssignments();
        Mockito.verify(grid, Mockito.never())
                .scrollToPositionWithOffset(Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    public void swappingInNotes_rebuildsTheColumns() {
        StaggeredGridLayoutManager grid =
                Mockito.spy(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        list.setLayoutManager(grid);
        Mockito.clearInvocations(grid);

        controller.prepareSwappedList(14, false);
        Mockito.verify(grid).invalidateSpanAssignments();
        Mockito.verify(grid, Mockito.never())
                .scrollToPositionWithOffset(Mockito.anyInt(), Mockito.anyInt());
        // Where the jump to the top lands is covered on real layouts by ListJumpToTopTest.
    }
}
