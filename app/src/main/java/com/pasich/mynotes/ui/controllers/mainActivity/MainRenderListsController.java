package com.pasich.mynotes.ui.controllers.mainActivity;

import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.ActivityMainBinding;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Shows the notes list, the empty state and the tags row.
 *
 * <p>Each of the three views is driven toward a target state ({@link ViewFader}), so overlapping
 * requests — a sync that empties a category and fills it again a moment later, or a tag switch
 * while the list is still fading — always settle on the latest one instead of leaving the screen
 * blank. A full content swap (another category, another sort order, a reordered grid) fades the
 * list out, replaces its content while nothing is visible, and fades it back in after the new
 * layout is ready, so cards never slide across each other.
 */
public class MainRenderListsController {

    private static final long LIST_SHOW_MS = 220;
    private static final long LIST_HIDE_MS = 160;
    private static final long LIST_DIM_MS = 110;
    private static final long EMPTY_SHOW_MS = 200;
    private static final long EMPTY_HIDE_MS = 150;
    private static final long TAGS_SHOW_MS = 240;
    private static final long TAGS_HIDE_MS = 180;

    private final RecyclerView listNotes;
    private final TextView emptyText;
    private final View emptyImage;
    private final Resources res;
    private final BooleanSupplier animationsEnabled;

    private final ViewFader listFader;
    private final ViewFader emptyFader;
    private final ViewFader tagsFader;

    /** True from the start of a swap until the swapped-in content has been laid out. */
    private boolean swapping;

    @Nullable private ViewTreeObserver.OnPreDrawListener pendingReveal;

    public MainRenderListsController(ActivityMainBinding binding) {
        this(
                binding.listNotes,
                binding.includeEmpty.emptyViewNote,
                binding.includeEmpty.emptyNotesText,
                binding.includeEmpty.imageEmpty,
                binding.listTags,
                ValueAnimator::areAnimatorsEnabled);
    }

    @VisibleForTesting
    public MainRenderListsController(
            RecyclerView listNotes,
            View emptyView,
            TextView emptyText,
            View emptyImage,
            View listTags,
            BooleanSupplier animationsEnabled) {
        this.listNotes = listNotes;
        this.emptyText = emptyText;
        this.emptyImage = emptyImage;
        this.res = listNotes.getResources();
        this.animationsEnabled = animationsEnabled;
        this.listFader = new ViewFader(listNotes, View.INVISIBLE, 0.97f, animationsEnabled);
        this.emptyFader = new ViewFader(emptyView, View.GONE, 1f, animationsEnabled);
        listTags.setPivotY(0f);
        this.tagsFader = new ViewFader(listTags, View.GONE, 0.85f, animationsEnabled);
    }

    /** Whether the system "remove animations" setting currently allows animation. */
    public boolean animationsEnabled() {
        return animationsEnabled.getAsBoolean();
    }

    /**
     * True while the list content is being replaced behind a fade. Item animations must not run in
     * that window: the new layout is meant to appear in one piece.
     */
    public boolean isSwapping() {
        return swapping;
    }

    /**
     * Shows the list when there are notes, the empty state otherwise. Safe to call any number of
     * times, in any order: the last call wins.
     *
     * @param animate false to apply the result at once (for example while the screen is not
     *     visible)
     */
    public void showStateNoteList(@Nullable Tag selectedTag, int notesCount, boolean animate) {
        if (notesCount == 0) {
            cancelReveal();
            swapping = false;
            listFader.hide(animate, LIST_HIDE_MS, new AccelerateInterpolator(1.4f));
            updateEmptyText(new EmptyStateBuilder().build(selectedTag, notesCount));
            emptyFader.show(animate, EMPTY_SHOW_MS, new DecelerateInterpolator());
            return;
        }

        emptyFader.hide(animate, EMPTY_HIDE_MS, new AccelerateInterpolator());
        if (swapping && animate) {
            revealAfterLayout();
        } else {
            cancelReveal();
            swapping = false;
            listFader.show(animate, LIST_SHOW_MS, new DecelerateInterpolator(1.6f));
        }
    }

    /**
     * Replaces the whole list content behind a short fade: {@code submit} runs once the list is no
     * longer visible, and must end by calling {@link #showStateNoteList} from its commit callback.
     * Runs {@code submit} immediately when there is nothing on screen to fade.
     */
    public void swapListContent(boolean animate, @NonNull Runnable submit) {
        if (!animate || !animationsEnabled() || !listFader.isTargetShown()) {
            submit.run();
            return;
        }
        swapping = true;
        cancelReveal();
        RecyclerView.ItemAnimator itemAnimator = listNotes.getItemAnimator();
        if (itemAnimator != null) itemAnimator.endAnimations();
        listFader.dim(true, LIST_DIM_MS, new AccelerateInterpolator(), submit);
    }

    /**
     * Readies the list for content just swapped in behind the fade: rebuilds the grid columns from
     * scratch so the grid comes back without gaps and, when {@code toTop}, puts the first note at
     * the top.
     *
     * <p>Does nothing for an empty list. Resetting the spans of a {@link
     * StaggeredGridLayoutManager} and asking it to scroll while it has no items leaves it anchored
     * wrong, and the next non-empty content was laid out below the screen: switching from an empty
     * tag back to all notes showed a blank list until the app was restarted.
     */
    public void prepareSwappedList(int notesCount, boolean toTop) {
        if (notesCount == 0) return;
        if (listNotes.getLayoutManager() instanceof StaggeredGridLayoutManager grid) {
            grid.invalidateSpanAssignments();
        }
        if (toTop) jumpToTop();
    }

    /** Smoothly scrolls the notes list to the top. */
    public void scrollUpNoteList() {
        listNotes.post(() -> listNotes.smoothScrollToPosition(0));
    }

    /** Puts the first note at the top in the next layout pass, without animation. */
    public void jumpToTop() {
        RecyclerView.LayoutManager lm = listNotes.getLayoutManager();
        if (lm instanceof StaggeredGridLayoutManager grid) {
            grid.scrollToPositionWithOffset(0, 0);
        } else if (lm != null) {
            lm.scrollToPosition(0);
        }
    }

    /** Shows the tags row only when the user has tags of their own. */
    public void renderListTags(List<Tag> tags, boolean animate) {
        boolean hasUserTags = false;
        for (Tag tag : tags) {
            if (!SystemTagsManager.isSystemTag(tag)) {
                hasUserTags = true;
                break;
            }
        }

        if (hasUserTags) {
            tagsFader.show(animate, TAGS_SHOW_MS, new FastOutSlowInInterpolator());
        } else {
            tagsFader.hide(animate, TAGS_HIDE_MS, new DecelerateInterpolator(1.6f));
        }
    }

    /** Cancels running animations and pending callbacks; call when the screen goes away. */
    public void release() {
        cancelReveal();
        listFader.cancel();
        emptyFader.cancel();
        tagsFader.cancel();
    }

    /**
     * Fades the swapped-in list back in right before its first frame is drawn, so the new layout is
     * complete (and item animations were skipped) by the time it becomes visible.
     */
    private void revealAfterLayout() {
        if (pendingReveal != null) return;
        ViewTreeObserver.OnPreDrawListener listener =
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        removeReveal(this);
                        swapping = false;
                        if (animationsEnabled()) listNotes.scheduleLayoutAnimation();
                        listFader.show(true, LIST_SHOW_MS, new DecelerateInterpolator(1.6f));
                        return true;
                    }
                };
        pendingReveal = listener;
        listNotes.getViewTreeObserver().addOnPreDrawListener(listener);
        listNotes.invalidate();
    }

    private void cancelReveal() {
        if (pendingReveal != null) removeReveal(pendingReveal);
    }

    private void removeReveal(ViewTreeObserver.OnPreDrawListener listener) {
        ViewTreeObserver observer = listNotes.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnPreDrawListener(listener);
        if (pendingReveal == listener) pendingReveal = null;
    }

    /**
     * Updates the empty state text based on the selected tag and note count. Also applies
     * density-specific adjustments for low DPI devices.
     */
    private void updateEmptyText(String text) {
        emptyText.setText(text);

        // Low-density devices optimization
        if (res.getDisplayMetrics().density < 2.2) {
            emptyImage.setVisibility(View.GONE);
        }
    }

    /** Helper class for constructing the appropriate empty-state text. */
    private class EmptyStateBuilder {
        String build(@Nullable Tag selectedTag, int notesCount) {
            if (notesCount > 0) return "";

            if (selectedTag == null || SystemTagsManager.isAllNotesTag(selectedTag)) {
                return res.getString(R.string.emptyNotes);
            }

            return res.getString(R.string.emptyNotesForTag, selectedTag.getNameTag());
        }
    }
}
