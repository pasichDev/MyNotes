package com.pasich.mynotes.data.preferences;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.cache.AppPreferencesCache;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.utils.backup.models.PreferencesBackup;
import org.junit.Before;
import org.junit.Test;

/**
 * When a stored theme mode is pushed to AppCompat.
 *
 * <p>Pushing it recreates every started activity. That is wanted for a theme arriving with a sync
 * and fatal during a backup restore, whose inserts are still running on the Backup screen: the
 * recreation disposed them and left the database half restored with no message.
 */
public class AppPreferencesHelperTest {

    private ThemePreferencesCache themeCache;
    private AppPreferencesHelper helper;

    @Before
    public void setUp() {
        AppPreferencesCache appCache = mock(AppPreferencesCache.class);
        themeCache = mock(ThemePreferencesCache.class);
        SafePreferences prefs = mock(SafePreferences.class);
        when(prefs.commitAll(any())).thenReturn(true);
        helper = new AppPreferencesHelper(appCache, themeCache, prefs, Runnable::run);
    }

    @Test
    public void aRestoreStoresTheThemeWithoutApplyingItMidWay() {
        helper.setListPreferences(backup());

        verify(themeCache).refresh();
        verify(themeCache, never()).applyCurrentThemeMode();
    }

    @Test
    public void aSyncAppliesAReceivedThemeAtOnce() {
        helper.commitListPreferences(backup());

        verify(themeCache).applyCurrentThemeMode();
    }

    private static PreferencesBackup backup() {
        return new PreferencesBackup(
                1, "sans", "date", 14, 11, false, 2, false, false, false, 1.0f);
    }
}
