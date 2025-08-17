package com.pasich.mynotes.utils.adapters.tagAdapter;

import android.view.View;

public interface OnItemClickListenerTag {
    void onClick(int position);

    void onLongClick(int position, View mView);
}