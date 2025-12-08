package com.pasich.mynotes.ui.controllers;

import android.content.res.Resources;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.ActivityMainBinding;
import com.pasich.mynotes.utils.managers.SystemTagsManager;

import java.util.List;

public class MainRenderListsController {

    private final ActivityMainBinding binding;
    private final Resources res;

    public MainRenderListsController(ActivityMainBinding binding) {
        this.binding = binding;
        this.res = binding.getRoot().getResources();
    }


    public void animateNoteListChange() {
        binding.listNotes.animate()
                .alpha(0f)
                .scaleY(0.97f)
                .setDuration(120)
                .withEndAction(() -> {
                    binding.listNotes.scheduleLayoutAnimation();
                    binding.listNotes.animate()
                            .alpha(1f)
                            .scaleY(1f)
                            .setInterpolator(new OvershootInterpolator(0.6f))
                            .setDuration(220)
                            .start();
                })
                .start();
    }

    /**
     * Handles switching between the notes list and the empty state view,
     * including fade/scale animations and optional smooth scrolling.
     */
    public void showStateNoteList(@Nullable Tag selectedTag, int mNotesCount) {
        if (mNotesCount == 0) {
            animateHideList(binding.listNotes);
            animateShowEmpty(binding.includeEmpty.emptyViewNote);
            updateEmptyText(new EmptyStateBuilder().build(selectedTag, mNotesCount));
            return;
        }

        animateHideEmpty(binding.includeEmpty.emptyViewNote);
        animateShowList(binding.listNotes);

    }

    public void scrollUpNoteList() {
        binding.listNotes.post(() -> binding.listNotes.smoothScrollToPosition(0));
    }

    /**
     * Smoothly shows the notes list using fade + scale animation.
     */
    private void animateShowList(View list) {
        if (list.getVisibility() == View.VISIBLE) return;

        list.setVisibility(View.VISIBLE);
        list.setAlpha(0f);
        list.setScaleY(0.95f);

        list.animate()
                .alpha(1f)
                .scaleY(1f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    /**
     * Smoothly hides the notes list with fade + scale collapse animation.
     */
    private void animateHideList(View list) {
        if (list.getVisibility() != View.VISIBLE) return;

        list.animate()
                .alpha(0f)
                .scaleY(0.95f)
                .setDuration(180)
                .setInterpolator(new AccelerateInterpolator(1.4f))
                .withEndAction(() -> list.setVisibility(View.INVISIBLE))
                .start();
    }

    /**
     * Shows the empty state view using a simple fade-in animation.
     */
    private void animateShowEmpty(View empty) {
        if (empty.getVisibility() == View.VISIBLE) return;

        empty.setVisibility(View.VISIBLE);
        empty.setAlpha(0f);

        empty.animate()
                .alpha(1f)
                .setDuration(200)
                .start();
    }

    /**
     * Hides the empty state view using fade-out animation.
     */
    private void animateHideEmpty(View empty) {
        if (empty.getVisibility() != View.VISIBLE) return;

        empty.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(() -> empty.setVisibility(View.GONE))
                .start();
    }

    /**
     * Updates the empty state text based on the selected tag and note count.
     * Also applies density-specific adjustments for low DPI devices.
     */
    private void updateEmptyText(String emptyText) {
        binding.includeEmpty.emptyNotesText.setText(emptyText);

        // Low-density devices optimization
        if (binding.getRoot().getResources().getDisplayMetrics().density < 2.2) {
            binding.includeEmpty.imageEmpty.setVisibility(View.GONE);
        }
    }

    /**
     * Expands a horizontal list (tags list) using soft scale + fade animation.
     */
    private void expandHorizontal(View view) {
        if (view.getVisibility() == View.VISIBLE) return;

        view.setVisibility(View.VISIBLE);
        view.setPivotY(0);
        view.setScaleY(0.85f);
        view.setAlpha(0f);

        view.animate()
                .scaleY(1f)
                .alpha(1f)
                .setDuration(260)
                .setInterpolator(new OvershootInterpolator(0.8f))
                .start();
    }

    /**
     * Collapses a horizontal list with fade + slight scale-down animation.
     */
    private void collapseHorizontal(View view) {
        if (view.getVisibility() == View.GONE) return;

        view.animate()
                .scaleY(0.85f)
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .withEndAction(() -> {
                    view.setVisibility(View.GONE);
                    view.setScaleY(1f);
                    view.setAlpha(1f);
                })
                .start();
    }

    /**
     * Handles rendering of the tags list with animations depending
     * on whether user-defined tags exist.
     */
    public void renderListTags(List<Tag> tags) {
        boolean hasUserTags = false;
        for (Tag tag : tags) {
            if (!SystemTagsManager.isSystemTag(tag)) {
                hasUserTags = true;
                break;
            }
        }

        if (hasUserTags) {
            expandHorizontal(binding.listTags);
        } else {
            collapseHorizontal(binding.listTags);
        }
    }

    /**
     * Helper class for constructing the appropriate empty-state text.
     */
    private class EmptyStateBuilder {
        String build(@Nullable Tag selectedTag, int notesCount) {
            if (notesCount > 0) return "";

            if (selectedTag == null || "allNotes".equals(selectedTag.getNameTag())) {
                return res.getString(R.string.emptyNotes);
            }

            return res.getString(R.string.emptyNotesForTag, selectedTag.getNameTag());
        }
    }
}
