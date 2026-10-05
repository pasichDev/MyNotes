package com.pasich.mynotes.utils.tool;

import android.view.MenuItem;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.AppPreferencesCache;
import javax.inject.Inject;

/**
 * Owns the notes layout preference (one column or a two-column grid) and keeps the "View options"
 * toolbar action showing the layout that is currently on screen.
 */
public class FormatListTool {

    /** One note per row. */
    public static final int FORMAT_LIST = 1;

    /** Two columns of cards. */
    public static final int FORMAT_GRID = 2;

    private final AppPreferencesCache cache;

    @Inject
    public FormatListTool(AppPreferencesCache cache) {
        this.cache = cache;
        this.cache.initialize();
    }

    /** The saved layout, {@link #FORMAT_LIST} or {@link #FORMAT_GRID}. */
    public int getFormat() {
        return cache.getFormatPref() == FORMAT_GRID ? FORMAT_GRID : FORMAT_LIST;
    }

    /** Shows the saved layout on the toolbar action. Call once the menu exists. */
    public void init(MenuItem button) {
        bind(button, getFormat());
    }

    /**
     * Saves {@code format} and updates the toolbar action.
     *
     * @return true when the layout actually changed
     */
    public boolean setFormat(int format, MenuItem button) {
        int normalized = format == FORMAT_GRID ? FORMAT_GRID : FORMAT_LIST;
        boolean changed = normalized != getFormat();
        if (changed) cache.setFormatPref(normalized);
        bind(button, normalized);
        return changed;
    }

    private static void bind(MenuItem button, int format) {
        if (button == null) return;
        button.setIcon(iconFor(format));
    }

    /** The icon of the layout itself: a list for {@link #FORMAT_LIST}, tiles for the grid. */
    @DrawableRes
    public static int iconFor(int format) {
        return format == FORMAT_GRID
                ? R.drawable.ic_edit_format_tiles
                : R.drawable.ic_edit_format_list;
    }

    /** What TalkBack reads for the toolbar action, including the current layout. */
    @StringRes
    public static int contentDescriptionFor(int format) {
        return format == FORMAT_GRID
                ? R.string.view_options_cd_grid
                : R.string.view_options_cd_list;
    }
}
