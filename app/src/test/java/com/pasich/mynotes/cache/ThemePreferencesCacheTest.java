package com.pasich.mynotes.cache;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pasich.mynotes.data.preferences.SafePreferences;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import org.junit.Test;
import org.mockito.Mockito;

public class ThemePreferencesCacheTest {

    @Test
    public void initialize_textSizeBelowSupportedRange_normalizesAndPersistsMinimum() {
        SafePreferences preferences = Mockito.mock(SafePreferences.class);
        when(preferences.getInt(PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_SIZE, 16)).thenReturn(0);
        ThemePreferencesCache cache = new ThemePreferencesCache(preferences);

        cache.initialize();

        assertThat(cache.getSizeTextNoteActivity()).isEqualTo(10);
        verify(preferences).putInt(PreferencesConfig.ARGUMENT_PREFERENCE_TEXT_SIZE, 10);
    }
}
