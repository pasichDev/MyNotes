package com.pasich.mynotes.utils.transition;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.core.content.res.ResourcesCompat;
import com.pasich.mynotes.R;

public class CopyNoteAnimationUtil {

    public static void runCopyAnimation(View root) {
        if (root == null) return;

        Context context = root.getContext();

        Drawable pulse =
                ResourcesCompat.getDrawable(
                        context.getResources(),
                        R.drawable.note_copy_pulse_base,
                        context.getTheme());

        if (pulse == null) return;

        root.setForeground(pulse);
        root.setForegroundGravity(Gravity.FILL);

        // Pulsate
        ValueAnimator alphaAnimator = ValueAnimator.ofInt(0, 180, 0);
        alphaAnimator.setDuration(750);
        alphaAnimator.setInterpolator(new DecelerateInterpolator());
        alphaAnimator.addUpdateListener(
                a -> {
                    int alpha = (int) a.getAnimatedValue();
                    pulse.setAlpha(alpha);
                    root.invalidate();
                });
        alphaAnimator.start();

        // Bounce
        root.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(120)
                .withEndAction(() -> root.animate().scaleX(1f).scaleY(1f).setDuration(150).start())
                .start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> root.setForeground(null), 820);
    }
}
