package com.pasich.mynotes.base.dialog;


import android.content.Context;
import android.os.Vibrator;
import android.view.View;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.pasich.mynotes.base.view.BaseView;

public abstract class BaseDialogBottomSheets extends BottomSheetDialogFragment implements BaseView {

    @Override
    public void setState(BottomSheetDialog dialog) {
        BaseView.super.setState(dialog);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void selectTheme() {

    }

    @Override
    public void dismiss() {
        super.dismiss();
    }


    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void vibrateOpenDialog(boolean vibrate) {
        if (vibrate) {
            Vibrator vibrator = (Vibrator) requireActivity().getSystemService(Context.VIBRATOR_SERVICE);

            if (vibrator.hasVibrator()) {
                vibrator.vibrate(50L);
            }
        }
    }


    @Override
    public void onInfoSnack(int resID, View view, int typeInfo, int time) {

    }
}