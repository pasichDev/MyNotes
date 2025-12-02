package com.pasich.mynotes.ui.controllers;

import android.view.View;

public class AnimationController {

    private static final long DURATION = 250;

    public void fadeIn(View view) {
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(DURATION).start();
    }

    public void fadeOut(View view, Runnable endAction) {
        if (view.getVisibility() != View.VISIBLE) {
            if (endAction != null) endAction.run();
            return;
        }

        view.animate()
                .alpha(0f)
                .setDuration(DURATION)
                .withEndAction(() -> {
                    view.setAlpha(1f);
                    if (endAction != null) endAction.run();
                })
                .start();
    }
}
