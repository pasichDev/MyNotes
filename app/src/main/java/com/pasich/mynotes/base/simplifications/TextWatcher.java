package com.pasich.mynotes.base.simplifications;

import android.text.Editable;

public abstract class TextWatcher implements android.text.TextWatcher {

    protected abstract void changeText(Editable s);

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        changeText(s);
    }
}
