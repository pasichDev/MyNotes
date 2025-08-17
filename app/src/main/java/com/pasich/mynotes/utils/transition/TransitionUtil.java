package com.pasich.mynotes.utils.transition;

import android.transition.Transition;
import android.view.View;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.transition.platform.MaterialArcMotion;
import com.google.android.material.transition.platform.MaterialContainerTransform;
import com.pasich.mynotes.R;

public class TransitionUtil {

    /**
     * Transition to activity Notes
     *
     * @param container - coordinationLyout
     */
    public static Transition buildContainerTransform(View container) {
        MaterialContainerTransform materialContainerTransform = new MaterialContainerTransform();
        materialContainerTransform.addTarget(container)
                .setDuration(300)
                .setInterpolator(new FastOutSlowInInterpolator());
        materialContainerTransform.setAllContainerColors(MaterialColors.getColor(container, R.attr.colorSurface));
        materialContainerTransform.setPathMotion(new MaterialArcMotion());
        materialContainerTransform.setFadeMode(MaterialContainerTransform.FADE_MODE_CROSS);
        return materialContainerTransform;
    }
}
