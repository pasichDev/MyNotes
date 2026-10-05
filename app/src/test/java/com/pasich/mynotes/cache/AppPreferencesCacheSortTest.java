package com.pasich.mynotes.cache;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.data.preferences.SafePreferences;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import com.pasich.mynotes.utils.constants.settings.SortParam;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/** The custom order is a device-local choice beside the synced date order. */
public class AppPreferencesCacheSortTest {

    private SafePreferences preferences;
    private AppPreferencesCache cache;

    @Before
    public void setUp() {
        preferences = Mockito.mock(SafePreferences.class);
        when(preferences.getString(
                        PreferencesConfig.ARGUMENT_PREFERENCE_SORT,
                        PreferencesConfig.ARGUMENT_DEFAULT_SORT_PREF))
                .thenReturn(SortParam.DataReserve);
        when(preferences.getString(
                        eq(PreferencesConfig.ARGUMENT_PREFERENCE_LAST_KNOWN_VERSION), anyString()))
                .thenReturn("0");
        cache = new AppPreferencesCache(preferences);
        cache.initialize();
    }

    @Test
    public void choosingCustom_keepsTheSyncedDateOrderAsItWas() {
        cache.setSortPref(SortParam.Custom);

        assertThat(cache.getSortPref()).isEqualTo(SortParam.Custom);
        // What is backed up and synced stays a value every version of the app understands.
        assertThat(cache.getSyncedSortPref()).isEqualTo(SortParam.DataReserve);
        verify(preferences).putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_CUSTOM_ORDER, true);
        verify(preferences, never())
                .putString(PreferencesConfig.ARGUMENT_PREFERENCE_SORT, SortParam.Custom);
    }

    @Test
    public void choosingADateOrder_leavesCustom() {
        cache.setSortPref(SortParam.Custom);
        cache.setSortPref(SortParam.DataSort);

        assertThat(cache.getSortPref()).isEqualTo(SortParam.DataSort);
        assertThat(cache.getSyncedSortPref()).isEqualTo(SortParam.DataSort);
        verify(preferences).putBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_CUSTOM_ORDER, false);
    }

    @Test
    public void theCustomChoiceSurvivesARestart() {
        when(preferences.getBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_CUSTOM_ORDER, false))
                .thenReturn(true);

        cache.refresh();

        assertThat(cache.getSortPref()).isEqualTo(SortParam.Custom);
        assertThat(cache.getSyncedSortPref()).isEqualTo(SortParam.DataReserve);
    }
}
