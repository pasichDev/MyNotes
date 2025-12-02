package com.pasich.mynotes.ui.controllers;

import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.appbar.AppBarLayout;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.ActivityMainBinding;

public class EmptyStateController {

    private final ActivityMainBinding binding;
    private final AnimationController animationController;

    public EmptyStateController(ActivityMainBinding binding,
                                AnimationController animationController) {
        this.binding = binding;
        this.animationController = animationController;
    }


    public void showState(boolean isEmpty, @Nullable Tag selectedTag) {

        updateEmptyText(selectedTag);
        updateAppBarBehavior(!isEmpty);

        if (isEmpty) {
            animationController.fadeOut(binding.listNotes, () -> {
                binding.listNotes.setVisibility(View.INVISIBLE);
                animationController.fadeIn(binding.includeEmpty.emptyViewNote);
            });

        } else {
            animationController.fadeOut(binding.includeEmpty.emptyViewNote, () -> {
                binding.includeEmpty.emptyViewNote.setVisibility(View.GONE);
                animationController.fadeIn(binding.listNotes);
            });
        }
    }

    private void updateEmptyText(Tag selectedTag) {
        if (selectedTag != null &&
                selectedTag.getSystemAction() != 2 &&
                !selectedTag.getNameTag().equals("allNotes")) {

            binding.includeEmpty.emptyNotesText.setText(
                    binding.getRoot().getContext().getString(
                            R.string.emptyNotesForTag,
                            selectedTag.getNameTag()
                    )
            );
        } else {
            binding.includeEmpty.emptyNotesText.setText(R.string.emptyNotes);
        }

        // low density hack
        if (binding.getRoot().getResources().getDisplayMetrics().density < 2.2) {
            binding.includeEmpty.imageEmpty.setVisibility(View.GONE);
        }
    }

    private void updateAppBarBehavior(boolean canScroll) {
        AppBarLayout.LayoutParams params =
                (AppBarLayout.LayoutParams) binding.actionSearch.getLayoutParams();

        if (canScroll) {
            params.setScrollFlags(
                    AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL |
                            AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
            );
        } else {
            params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL);
            binding.appBarMainActivity.setExpanded(true, true);
        }

        binding.actionSearch.setLayoutParams(params);
    }
}
