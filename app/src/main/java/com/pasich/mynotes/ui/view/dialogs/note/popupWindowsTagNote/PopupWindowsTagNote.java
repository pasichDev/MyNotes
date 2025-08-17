package com.pasich.mynotes.ui.view.dialogs.note.popupWindowsTagNote;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;

import androidx.core.content.ContextCompat;

import com.pasich.mynotes.R;
import com.pasich.mynotes.databinding.ViewPopupTagNoteBinding;

public class PopupWindowsTagNote {

    private final PopupWindow mPopupWindows;
    private final ViewPopupTagNoteBinding mBinding;
    private final int widthDisplay = Resources.getSystem().getDisplayMetrics().widthPixels;
    private final int widthAnchor;
    private View mAnchor;
    private PopupWindowsTagNoteOnClickListener mListener;

    public PopupWindowsTagNote(LayoutInflater layoutInflater, View anchor, PopupWindowsTagNoteOnClickListener listener) {
        this.mListener = listener;
        this.mBinding = ViewPopupTagNoteBinding.inflate(layoutInflater);
        this.mAnchor = anchor;
        this.widthAnchor = anchor.getWidth();
        this.mPopupWindows = new PopupWindow(mBinding.getRoot(), RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT, true);

        mBinding.getRoot().measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

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
        initListeners();
        getPopupWindows().showAsDropDown(mAnchor, xof, 10);
    }


    private void initListeners() {
        mBinding.createNoteTag.setOnClickListener(v -> {
            mListener.createNoteTag();
            getPopupWindows().dismiss();

        });

    }


    public PopupWindow getPopupWindows() {
        return mPopupWindows;
    }

    public void setOnDismissListener() {
        mListener = null;
        mAnchor = null;
        mBinding.createNoteTag.setOnClickListener(null);

    }
}
