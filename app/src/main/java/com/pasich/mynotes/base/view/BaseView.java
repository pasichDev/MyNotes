package com.pasich.mynotes.base.view;

import android.view.View;

import androidx.annotation.StringRes;

import com.google.android.material.bottomsheet.BottomSheetDialog;

public interface BaseView {

    void initListeners();

    void selectTheme();

    void onInfoSnack(@StringRes int resID, View view, int typeInfo, int time);

    default void redrawActivity() {
    }

    default void vibrateOpenDialog(boolean vibrate) {

    }

    default void setState(BottomSheetDialog dialog) {

    }
}
