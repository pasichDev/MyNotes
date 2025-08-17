package com.pasich.mynotes.base.simplifications;

import android.widget.SeekBar;

public abstract class OnSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {

    protected abstract void changeProgress(int progress);

    protected abstract void stopChangeProgress(SeekBar seekBar);

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        changeProgress(progress);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        stopChangeProgress(seekBar);
    }
}
