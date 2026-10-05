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

    @Test
    public void aLongNoteWithThreeChangedLines_marksOnlyThoseLines() {
        StringBuilder before = new StringBuilder("Title");
        StringBuilder after = new StringBuilder("Title");
        for (int i = 1; i <= 60; i++) {
            before.append("\nLine ").append(i).append(" of the note text");
            String changed = i == 5 || i == 30 || i == 55 ? "edited " : "";
            after.append("\nLine ")
                    .append(i)
                    .append(" of the ")
                    .append(changed)
                    .append("note text");
        }
        String a = before.toString();
        String b = after.toString();

        java.util.List<int[]> inAfter = NoteVersionText.changedRanges(b, a);
        assertThat(inAfter).hasSize(3);
        int marked = 0;
        for (int[] range : inAfter) {
            assertThat(b.substring(range[0], range[1])).isEqualTo("edited ");
            marked += range[1] - range[0];
        }
        assertThat(marked).isLessThan(b.length() / 20);
        // Words were only added: nothing in the older text is marked.
        assertThat(NoteVersionText.changedRanges(a, b)).isEmpty();
    }

    @Test
    public void aChangedWordInALongNote_marksThatWordOnBothSides() {
        StringBuilder before = new StringBuilder();
        StringBuilder after = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            before.append("Row ").append(i).append(i == 40 ? " total 1200" : " text").append('\n');
            after.append("Row ").append(i).append(i == 40 ? " total 1500" : " text").append('\n');
        }
        String a = before.toString();
        String b = after.toString();

        java.util.List<int[]> inBefore = NoteVersionText.changedRanges(a, b);
        assertThat(inBefore).hasSize(1);
        assertThat(a.substring(inBefore.get(0)[0], inBefore.get(0)[1])).isEqualTo("1200");
        java.util.List<int[]> inAfter = NoteVersionText.changedRanges(b, a);
        assertThat(b.substring(inAfter.get(0)[0], inAfter.get(0)[1])).isEqualTo("1500");
    }
}
