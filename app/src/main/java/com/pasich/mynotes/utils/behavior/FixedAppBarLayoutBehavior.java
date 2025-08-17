package com.pasich.mynotes.utils.behavior;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

import com.google.android.material.appbar.AppBarLayout;

public class FixedAppBarLayoutBehavior extends AppBarLayout.Behavior {

    public FixedAppBarLayoutBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDragCallback(new DragCallback() {
            @Override
            public boolean canDrag(@NonNull AppBarLayout appBarLayout) {
                return false;
            }
        });
    }
}