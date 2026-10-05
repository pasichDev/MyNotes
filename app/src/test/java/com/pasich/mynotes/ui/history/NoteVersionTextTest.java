package com.pasich.mynotes.ui.history;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NoteVersionTextTest {

    @Test
    public void underAMinute_isJustNow() {
        long now = 1_000_000_000L;
        assertThat(NoteVersionText.isJustNow(now, now - 59_000)).isTrue();
        assertThat(NoteVersionText.isJustNow(now, now)).isTrue();
        assertThat(NoteVersionText.isJustNow(now, now - 60_000)).isFalse();
    }

    @Test
    public void aVersionKeptSecondsAgo_doesNotSayZeroMinutes() {
        Context context = RuntimeEnvironment.getApplication();
        String when = NoteVersionText.when(context, System.currentTimeMillis() - 5_000).toString();
        assertThat(when).startsWith("Just now");
        assertThat(when).doesNotContain("0 minutes");
    }

    @Test
    public void aChangedDigit_marksTheWholeNumber() {
        // "1200" -> "1500": the texts differ only in the second character.
        assertThat(NoteVersionText.wholeWords("Total 1200 UAH", 7, 8))
                .asList()
                .containsExactly(6, 10)
                .inOrder();
    }

    @Test
    public void aChangeInsideAWord_marksTheWord() {
        assertThat(NoteVersionText.wholeWords("buy milk today", 6, 7))
                .asList()
                .containsExactly(4, 8)
                .inOrder();
    }

    @Test
    public void lettersAddedOnTheOtherSide_markTheWordTheyWentInto() {
        // An empty range inside "milk".
        assertThat(NoteVersionText.wholeWords("buy milk", 6, 6))
                .asList()
                .containsExactly(4, 8)
                .inOrder();
    }

    @Test
    public void aChangeOnWordBoundaries_staysAsItIs() {
        assertThat(NoteVersionText.wholeWords("buy milk now", 3, 8))
                .asList()
                .containsExactly(3, 8)
                .inOrder();
        assertThat(NoteVersionText.wholeWords("buy milk", 3, 3))
                .asList()
                .containsExactly(3, 3)
                .inOrder();
    }
}
