package com.pasich.mynotes.ui.controllers.mainActivity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.function.BooleanSupplier;

/**
 * Moves one view toward a shown or hidden target.
 *
 * <p>Every request starts from wherever the view is right now and cancels the animation that was
 * running, so a hide that is still in flight can never finish after a later show and leave the view
 * invisible. Asking for the target the view is already heading to is a no-op. With system
 * animations turned off the target is applied immediately.
 */
final class ViewFader {

    private final View view;
    private final int hiddenVisibility;
    private final float hiddenScaleY;
    private final BooleanSupplier animationsEnabled;

    @Nullable private Animator running;
    @Nullable private Runnable onDimmed;
    private float runningTargetAlpha = -1f;
    private boolean targetShown;
    private boolean dimmed;

    ViewFader(
            View view,
            int hiddenVisibility,
            float hiddenScaleY,
            BooleanSupplier animationsEnabled) {
        this.view = view;
        this.hiddenVisibility = hiddenVisibility;
        this.hiddenScaleY = hiddenScaleY;
        this.animationsEnabled = animationsEnabled;
        this.targetShown = view.getVisibility() == View.VISIBLE;
    }

    /** True while the view is meant to end up visible (including while it is dimmed). */
    boolean isTargetShown() {
        return targetShown;
    }

    /** True from {@link #dim} until the next {@link #show} or {@link #hide}. */
    boolean isDimmed() {
        return dimmed;
    }

    void show(boolean animate, long durationMs, TimeInterpolator interpolator) {
        if (targetShown && !dimmed && (running != null || isAtRest(1f, 1f, View.VISIBLE))) {
            return;
        }
        targetShown = true;
        dimmed = false;
        onDimmed = null;
        cancel();
        if (view.getVisibility() != View.VISIBLE) {
            view.setAlpha(0f);
            view.setScaleY(hiddenScaleY);
            view.setVisibility(View.VISIBLE);
        }
        animateTo(1f, 1f, animate, durationMs, interpolator, null);
    }

    void hide(boolean animate, long durationMs, TimeInterpolator interpolator) {
        if (!targetShown && (running != null || view.getVisibility() == hiddenVisibility)) {
            return;
        }
        targetShown = false;
        dimmed = false;
        onDimmed = null;
        cancel();
        if (view.getVisibility() != View.VISIBLE) {
            settleHidden();
            return;
        }
        animateTo(0f, hiddenScaleY, animate, durationMs, interpolator, this::settleHidden);
    }

    /**
     * Fades a shown view out without hiding it, then runs {@code whenDimmed}. A second call while
     * the fade is running only replaces the callback, so the newest one wins. When the view is not
     * on screen, or animations are off, {@code whenDimmed} runs right away.
     */
    void dim(boolean animate, long durationMs, TimeInterpolator interpolator, Runnable whenDimmed) {
        if (!targetShown || view.getVisibility() != View.VISIBLE || !canAnimate(animate)) {
            whenDimmed.run();
            return;
        }
        boolean alreadyDark = dimmed && running == null && view.getAlpha() == 0f;
        dimmed = true;
        if (alreadyDark) {
            onDimmed = null;
            whenDimmed.run();
            return;
        }
        onDimmed = whenDimmed;
        // Already fading out: keep going and let the newest callback run at the end.
        if (isHeadingTo(0f)) return;
        cancel();
        animateTo(
                0f,
                1f,
                true,
                durationMs,
                interpolator,
                () -> {
                    Runnable callback = onDimmed;
                    onDimmed = null;
                    if (callback != null) callback.run();
                });
    }

    /** Stops any running animation without applying its end state. */
    void cancel() {
        Animator animator = running;
        running = null;
        runningTargetAlpha = -1f;
        if (animator != null) animator.cancel();
    }

    private boolean isHeadingTo(float alpha) {
        return running != null && runningTargetAlpha == alpha;
    }

    private boolean isAtRest(float alpha, float scaleY, int visibility) {
        return view.getVisibility() == visibility
                && view.getAlpha() == alpha
                && view.getScaleY() == scaleY;
    }

    private boolean canAnimate(boolean animate) {
        return animate && animationsEnabled.getAsBoolean();
    }

    private void settleHidden() {
        view.setVisibility(hiddenVisibility);
        view.setAlpha(1f);
        view.setScaleY(1f);
    }

    private void animateTo(
            float alpha,
            float scaleY,
            boolean animate,
            long durationMs,
            TimeInterpolator interpolator,
            @Nullable Runnable endAction) {
        if (!canAnimate(animate)) {
            view.setAlpha(alpha);
            view.setScaleY(scaleY);
            if (endAction != null) endAction.run();
            return;
        }
        ObjectAnimator animator =
                ObjectAnimator.ofPropertyValuesHolder(
                        view,
                        PropertyValuesHolder.ofFloat(View.ALPHA, alpha),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, scaleY));
        animator.setDuration(durationMs);
        animator.setInterpolator(interpolator);
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // A cancelled or replaced animation is no longer `running`.
                        if (running != animation) return;
                        running = null;
                        runningTargetAlpha = -1f;
                        if (endAction != null) endAction.run();
                    }
                });
        running = animator;
        runningTargetAlpha = alpha;
        animator.start();
    }
}
