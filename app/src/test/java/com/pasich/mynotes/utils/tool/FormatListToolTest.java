package com.pasich.mynotes.utils.tool;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.view.MenuItem;
import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.AppPreferencesCache;
import org.junit.Before;
import org.junit.Test;

public class FormatListToolTest {

    private AppPreferencesCache cache;
    private MenuItem item;

    @Before
    public void setUp() {
        cache = mock(AppPreferencesCache.class);
        item = mock(MenuItem.class);
    }

    @Test
    public void init_showsTheSavedListLayout() {
        when(cache.getFormatPref()).thenReturn(FormatListTool.FORMAT_LIST);

        new FormatListTool(cache).init(item);

        verify(item).setIcon(R.drawable.ic_edit_format_list);
    }

    @Test
    public void init_showsTheSavedGridLayout() {
        when(cache.getFormatPref()).thenReturn(FormatListTool.FORMAT_GRID);

        new FormatListTool(cache).init(item);

        verify(item).setIcon(R.drawable.ic_edit_format_tiles);
    }

    @Test
    public void unknownSavedValue_fallsBackToList() {
        when(cache.getFormatPref()).thenReturn(0);

        FormatListTool tool = new FormatListTool(cache);

        assertThat(tool.getFormat()).isEqualTo(FormatListTool.FORMAT_LIST);
    }

    @Test
    public void setFormat_savesTheNewLayoutAndUpdatesTheIcon() {
        when(cache.getFormatPref()).thenReturn(FormatListTool.FORMAT_LIST);

        boolean changed = new FormatListTool(cache).setFormat(FormatListTool.FORMAT_GRID, item);

        assertThat(changed).isTrue();
        verify(cache).setFormatPref(FormatListTool.FORMAT_GRID);
        verify(item).setIcon(R.drawable.ic_edit_format_tiles);
    }

    @Test
    public void setFormat_sameLayout_savesNothing() {
        when(cache.getFormatPref()).thenReturn(FormatListTool.FORMAT_GRID);

        boolean changed = new FormatListTool(cache).setFormat(FormatListTool.FORMAT_GRID, item);

        assertThat(changed).isFalse();
        verify(cache, never()).setFormatPref(FormatListTool.FORMAT_GRID);
    }

    @Test
    public void contentDescription_namesTheCurrentLayout() {
        assertThat(FormatListTool.contentDescriptionFor(FormatListTool.FORMAT_LIST))
                .isEqualTo(R.string.view_options_cd_list);
        assertThat(FormatListTool.contentDescriptionFor(FormatListTool.FORMAT_GRID))
                .isEqualTo(R.string.view_options_cd_grid);
    }
}
