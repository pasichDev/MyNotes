package com.pasich.mynotes.cache;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import com.pasich.mynotes.cache.NoteOpeningPreferences.OpenMode;
import com.pasich.mynotes.cache.NoteOpeningPreferences.OpenPosition;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NoteOpeningPreferencesTest {

    private Context context;
    private NoteOpeningPreferences prefs;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        prefs = new NoteOpeningPreferences(context);
    }

    @Test
    public void defaults_keepTodaysBehaviour() {
        assertThat(prefs.getOpenMode()).isEqualTo(OpenMode.AUTO);
        assertThat(prefs.getOpenPosition()).isEqualTo(OpenPosition.LAST);
        assertThat(prefs.restoresLastPosition()).isTrue();
        assertThat(prefs.isDoubleTapToEdit()).isFalse();
    }

    @Test
    public void choicesSurviveANewInstance() {
        prefs.setOpenMode(OpenMode.READ);
        prefs.setOpenPosition(OpenPosition.START);
        prefs.setDoubleTapToEdit(true);

        NoteOpeningPreferences reread = new NoteOpeningPreferences(context);

        assertThat(reread.getOpenMode()).isEqualTo(OpenMode.READ);
        assertThat(reread.getOpenPosition()).isEqualTo(OpenPosition.START);
        assertThat(reread.restoresLastPosition()).isFalse();
        assertThat(reread.isDoubleTapToEdit()).isTrue();
    }

    @Test
    public void unknownStoredValues_fallBackToTheDefaults() {
        context.getSharedPreferences(NoteOpeningPreferences.FILE, Context.MODE_PRIVATE)
                .edit()
                .putString(NoteOpeningPreferences.KEY_MODE, "SPLIT_VIEW")
                .putString(NoteOpeningPreferences.KEY_POSITION, "MIDDLE")
                .commit();

        assertThat(prefs.getOpenMode()).isEqualTo(OpenMode.AUTO);
        assertThat(prefs.getOpenPosition()).isEqualTo(OpenPosition.LAST);
    }

    @Test
    public void keptOutOfTheSyncedSettingsFile() {
        prefs.setOpenMode(OpenMode.EDIT);

        assertThat(
                        context.getSharedPreferences(
                                        "com.pasich.mynotes_preferences", Context.MODE_PRIVATE)
                                .getAll()
                                .containsKey(NoteOpeningPreferences.KEY_MODE))
                .isFalse();
    }

    @Test
    public void auto_readsInTheSimpleEditor_editsInTheExtendedOne() {
        assertThat(NoteOpeningPreferences.opensInEditMode(OpenMode.AUTO, false, false)).isFalse();
        assertThat(NoteOpeningPreferences.opensInEditMode(OpenMode.AUTO, true, false)).isTrue();
    }

    @Test
    public void readAndEdit_applyToBothEditors() {
        assertThat(NoteOpeningPreferences.opensInEditMode(OpenMode.READ, false, false)).isFalse();
        assertThat(NoteOpeningPreferences.opensInEditMode(OpenMode.READ, true, false)).isFalse();
        assertThat(NoteOpeningPreferences.opensInEditMode(OpenMode.EDIT, false, false)).isTrue();
        assertThat(NoteOpeningPreferences.opensInEditMode(OpenMode.EDIT, true, false)).isTrue();
    }

    @Test
    public void newNotes_alwaysOpenForEditing() {
        for (OpenMode mode : OpenMode.values()) {
            assertThat(NoteOpeningPreferences.opensInEditMode(mode, false, true)).isTrue();
            assertThat(NoteOpeningPreferences.opensInEditMode(mode, true, true)).isTrue();
        }
    }
}
