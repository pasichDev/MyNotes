package com.pasich.mynotes.ui.view.dialogs.main.popupWindowsTag;


import android.content.Context;
import android.content.res.Resources;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;

import androidx.core.content.ContextCompat;

import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.ViewPopupTagBinding;

public class PopupWindowsTag {

    private final PopupWindow mPopupWindows;
    private final ViewPopupTagBinding mBinding;
    private final int widthDisplay = Resources.getSystem().getDisplayMetrics().widthPixels;
    private final int widthAnchor;
    private final Tag mTag;
    private View mAnchor;
    private PopupWindowsTagOnClickListener mListener;

    public PopupWindowsTag(LayoutInflater layoutInflater, View anchor, Tag tag, PopupWindowsTagOnClickListener listener) {
        this.mTag = tag;
        this.mListener = listener;
        this.mBinding = ViewPopupTagBinding.inflate(layoutInflater);
        this.mAnchor = anchor;
        this.widthAnchor = anchor.getWidth();
        this.mPopupWindows = new PopupWindow(mBinding.getRoot(), RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT, true);

        mBinding.getRoot().measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        onVibrate();
        onSettingsView();
    }


    private void onSettingsView() {
        int xof, widthDisplayCenter = widthDisplay / 2;
        if (mAnchor.getX() > widthDisplayCenter) {
            mBinding.getRoot().setBackground(ContextCompat.getDrawable(mBinding.getRoot().getContext(), R.drawable.background_popup_tag_right));
            xof = (int) (-mBinding.getRoot().getMeasuredWidth() * 0.9);
        } else {
            mBinding.getRoot().setBackground(ContextCompat.getDrawable(mBinding.getRoot().getContext(), R.drawable.background_popup_tag_left));
            xof = widthAnchor / 3;
        }
        getPopupWindows().setElevation(20);
        getPopupWindows().setOnDismissListener(this::setOnDismissListener);
        editVisibleConfiguration();
        initListeners();
        getPopupWindows().showAsDropDown(mAnchor, xof, 30);
    }


    private void editVisibleConfiguration() {
        mBinding.imageTagVisible.setImageResource(mTag.getVisibility() == 1 ? R.drawable.ic_show : R.drawable.ic_hide);
        mBinding.textVisibilityTag.setText(mTag.getVisibility() == 1 ? R.string.visibleTag : R.string.hiddeTag);


    }


    private void onVibrate() {
        Vibrator vibrator = (Vibrator) mBinding.getRoot().getContext().getSystemService(Context.VIBRATOR_SERVICE);

        if (vibrator.hasVibrator()) {
            vibrator.vibrate(100L);
        }
    }


    private void initListeners() {
        mBinding.deleteTag.setOnClickListener(v -> {
            mListener.deleteTag();
            getPopupWindows().dismiss();

        });
        mBinding.renameTag.setOnClickListener(v -> {
            mListener.renameTag();
            getPopupWindows().dismiss();
        });
        mBinding.visibleTag.setOnClickListener(v -> {
            mListener.visibleEditTag();
            getPopupWindows().dismiss();
        });

    }


    public PopupWindow getPopupWindows() {
        return mPopupWindows;
    }

    public void setOnDismissListener() {
        mListener = null;
        mAnchor = null;
        mBinding.deleteTag.setOnClickListener(null);
        mBinding.renameTag.setOnClickListener(null);
        mBinding.visibleTag.setOnClickListener(null);

    }
}
