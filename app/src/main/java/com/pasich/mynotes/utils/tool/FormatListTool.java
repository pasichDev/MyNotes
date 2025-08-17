package com.pasich.mynotes.utils.tool;

import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_DEFAULT_FORMAT_VALUE;
import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_PREFERENCE_FORMAT;

import android.view.MenuItem;

import com.pasich.mynotes.R;
import com.preference.PowerPreference;

import javax.inject.Inject;

public class FormatListTool {
    @Inject
    public FormatListTool() {
    }

    public void init(MenuItem button) {
        button.setIcon(getParamIco(getParamFormatValue()));
    }

    private int getParamFormatValue() {
        return PowerPreference.getDefaultFile().getInt(ARGUMENT_PREFERENCE_FORMAT, ARGUMENT_DEFAULT_FORMAT_VALUE);
    }

    /**
     * The switch itself, which switches the operating mode depending on the selected parameter
     */
    public void formatNote(MenuItem menuItem) {
        switch (getParamFormatValue()) {
            case 1 -> {
                PowerPreference.getDefaultFile().setInt(ARGUMENT_PREFERENCE_FORMAT, 2);
                menuItem.setIcon(getParamIco(2));
            }
            case 2 -> {
                PowerPreference.getDefaultFile().setInt(ARGUMENT_PREFERENCE_FORMAT, 1);
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
