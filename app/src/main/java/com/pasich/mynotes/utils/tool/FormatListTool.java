package com.pasich.mynotes.utils.tool;

import android.view.MenuItem;

import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.AppPreferencesCache;

import javax.inject.Inject;

public class FormatListTool {
    private final AppPreferencesCache cache;

    @Inject
    public FormatListTool(AppPreferencesCache cache) {
        this.cache = cache;
        this.cache.initialize();
    }

    public void init(MenuItem button) {
        button.setIcon(getParamIco(getParamFormatValue()));
    }

    private int getParamFormatValue() {
        return cache.getFormatPref();
    }

    /**
     * The switch itself, which switches the operating mode depending on the selected parameter
     */
    public void formatNote(MenuItem menuItem) {
        switch (getParamFormatValue()) {
            case 1 -> {
                cache.setFormatPref(2);
                menuItem.setIcon(getParamIco(2));
            }
            case 2 -> {
                cache.setFormatPref(1);
                menuItem.setIcon(getParamIco(1));
            }
        }
    }

    /**
     * Method to return int drawable
     *
     * @param param - The option chosen by the user
     * @return - int drawable
     */
    private int getParamIco(int param) {
        if (param == 2) {
            return R.drawable.ic_edit_format_list;
        }
        return R.drawable.ic_edit_format_tiles;

    }
}
