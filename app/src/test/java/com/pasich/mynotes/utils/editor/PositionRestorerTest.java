package com.pasich.mynotes.utils.editor;

import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class PositionRestorerTest {

    private static final int NONE = EditorCursor.NONE;

    /** A long note whose paragraphs are all different, so any 32 characters occur once. */
    private static String longNote() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            text.append("Paragraph ").append(i).append(" talks about item number ").append(i * 7);
            text.append(".\n");
        }
        return text.toString();
    }

    // ---------------------------------------------------------------------------------------
    // Simple editor
    // ---------------------------------------------------------------------------------------

    @Test
    public void unchangedText_restoresExactly() {
        String text = longNote();
        NoteViewState.Simple saved = PositionRestorer.captureSimple(text, 900, 910, 800, 1200, .4f);

        PositionRestorer.SimpleTarget target = PositionRestorer.restoreSimple(saved, text);

        assertThat(target.match).isEqualTo(PositionRestorer.Match.EXACT);
        assertThat(target.selectionStart).isEqualTo(900);
        assertThat(target.selectionEnd).isEqualTo(910);
        assertThat(target.topOffset).isEqualTo(800);
    }

    @Test
    public void textInsertedAbove_followsTheTextAroundTheCaret() {
        String text = longNote();
        NoteViewState.Simple saved = PositionRestorer.captureSimple(text, 900, 900, 800, 0, 0f);
        String inserted = "A new first line written on another device.\n";

        PositionRestorer.SimpleTarget target =
                PositionRestorer.restoreSimple(saved, inserted + text);

        assertThat(target.match).isEqualTo(PositionRestorer.Match.ANCHOR);
        assertThat(target.selectionStart).isEqualTo(900 + inserted.length());
        assertThat(target.selectionEnd).isEqualTo(target.selectionStart);
        assertThat(target.topOffset).isEqualTo(800 + inserted.length());
    }

    @Test
    public void textAroundTheCaretEdited_findsItByTheSideThatSurvived() {
        String text = longNote();
        int caret = 900;
        NoteViewState.Simple saved =
                PositionRestorer.captureSimple(text, caret, caret, NONE, 0, 0f);
        // The words just before the caret were rewritten; the text after it is untouched.
        String edited = text.substring(0, caret - 10) + "REWRITTEN!" + text.substring(caret);

        PositionRestorer.SimpleTarget target = PositionRestorer.restoreSimple(saved, edited);

        assertThat(target.match).isEqualTo(PositionRestorer.Match.ANCHOR);
        assertThat(target.selectionStart).isEqualTo(caret);
    }

    @Test
    public void repeatedContext_picksTheOccurrenceNearestTheOldPosition() {
        String block = "same words over and over again here\n";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 20; i++) text.append(block);
        int caret = block.length() * 12;
        NoteViewState.Simple saved =
                PositionRestorer.captureSimple(text.toString(), caret, caret, NONE, 0, 0f);

        PositionRestorer.SimpleTarget target = PositionRestorer.restoreSimple(saved, "x" + text);

        assertThat(target.selectionStart).isEqualTo(caret + 1);
    }

    @Test
    public void shortenedText_withContextGone_isClampedIntoRange() {
        String text = longNote();
        NoteViewState.Simple saved =
                PositionRestorer.captureSimple(text, text.length(), text.length(), 2000, 0, 0f);
        // A modest change that removed the end of the note, including the caret's context.
        String shorter = text.substring(0, (int) (text.length() * 0.7));

        PositionRestorer.SimpleTarget target = PositionRestorer.restoreSimple(saved, shorter);

        assertThat(target.match).isEqualTo(PositionRestorer.Match.CLAMPED);
        assertThat(target.selectionStart).isAtLeast(0);
        assertThat(target.selectionStart).isAtMost(shorter.length());
        assertThat(target.topOffset).isAtMost(shorter.length());
    }

    @Test
    public void replacedNote_opensAtTheTop() {
        String text = longNote();
        NoteViewState.Simple saved = PositionRestorer.captureSimple(text, 900, 900, 800, 0, 0f);

        PositionRestorer.SimpleTarget target =
                PositionRestorer.restoreSimple(saved, "Completely different and short.");

        assertThat(target.match).isEqualTo(PositionRestorer.Match.TOP);
        assertThat(target.hasSelection()).isFalse();
        assertThat(target.topOffset).isEqualTo(NONE);
    }

    @Test
    public void emptiedNote_opensAtTheTop() {
        NoteViewState.Simple saved = PositionRestorer.captureSimple(longNote(), 50, 50, 40, 0, 0f);

        assertThat(PositionRestorer.restoreSimple(saved, "").match)
                .isEqualTo(PositionRestorer.Match.TOP);
    }

    @Test
    public void nothingSaved_opensAtTheTop() {
        assertThat(PositionRestorer.restoreSimple(null, longNote()).match)
                .isEqualTo(PositionRestorer.Match.TOP);
    }

    @Test
    public void titleOnScreenAndNotEditing_opensAtTheTop() {
        NoteViewState.Simple saved =
                PositionRestorer.captureSimple(longNote(), NONE, NONE, NONE, 0, 0f);

        assertThat(PositionRestorer.restoreSimple(saved, longNote()).match)
                .isEqualTo(PositionRestorer.Match.TOP);
    }

    @Test
    public void readingOnly_restoresTheViewportWithoutACaret() {
        String text = longNote();
        NoteViewState.Simple saved = PositionRestorer.captureSimple(text, NONE, NONE, 1500, 0, 0f);

        PositionRestorer.SimpleTarget target = PositionRestorer.restoreSimple(saved, text);

        assertThat(target.hasSelection()).isFalse();
        assertThat(target.topOffset).isEqualTo(1500);
    }

    @Test
    public void caretFoundButTopContextGone_viewportFollowsTheCaret() {
        String text = longNote();
        NoteViewState.Simple saved = PositionRestorer.captureSimple(text, 900, 900, 600, 0, 0f);
        // Everything between the top of the screen and just before the caret was rewritten.
        String edited = text.substring(0, 560) + "#".repeat(300) + text.substring(860);

        PositionRestorer.SimpleTarget target = PositionRestorer.restoreSimple(saved, edited);

        assertThat(target.match).isEqualTo(PositionRestorer.Match.ANCHOR);
        assertThat(target.topOffset).isEqualTo(target.selectionStart);
    }

    @Test
    public void tamperedOffsetsAreNeverOutOfRange() {
        String text = longNote();
        NoteViewState.Simple saved = PositionRestorer.captureSimple(text, 10, 10, 10, 0, 0f);
        // A stored entry from an older build or a corrupted file.
        saved.selectionStart = 1_000_000;
        saved.selectionEnd = 2_000_000;
        saved.topOffset = 3_000_000;

        PositionRestorer.SimpleTarget target = PositionRestorer.restoreSimple(saved, text);

        assertThat(target.selectionStart).isAtMost(text.length());
        assertThat(target.selectionEnd).isAtMost(text.length());
        assertThat(target.topOffset).isAtMost(text.length());
    }

    @Test
    public void offsetsNeverSplitASurrogatePair() {
        String text = "start 😀 end of a reasonably long line of text";
        int insideEmoji = text.indexOf("😀") + 1;

        assertThat(PositionRestorer.snap(text, insideEmoji)).isEqualTo(insideEmoji - 1);
    }

    @Test
    public void captureNormalisesAReversedSelection() {
        NoteViewState.Simple saved =
                PositionRestorer.captureSimple(longNote(), 40, 10, NONE, -5, 2f);

        assertThat(saved.selectionStart).isEqualTo(10);
        assertThat(saved.selectionEnd).isEqualTo(40);
        assertThat(saved.scrollY).isEqualTo(0);
        assertThat(saved.scrollRatio).isEqualTo(1f);
    }

    @Test
    public void hashIsStableAndSensitive() {
        assertThat(PositionRestorer.hash("note")).isEqualTo(PositionRestorer.hash("note"));
        assertThat(PositionRestorer.hash("note")).isNotEqualTo(PositionRestorer.hash("nose"));
    }

    // ---------------------------------------------------------------------------------------
    // Extended editor
    // ---------------------------------------------------------------------------------------

    private static NoteViewState.Extended extended(
            String caretId, int caretOffset, String topId, int topIndex, int topPx) {
        NoteViewState.Extended state = new NoteViewState.Extended();
        state.caretBlockId = caretId;
        state.caretOffset = caretOffset;
        state.topBlockId = topId;
        state.topBlockIndex = topIndex;
        state.topOffsetPx = topPx;
        return state;
    }

    @Test
    public void extended_blocksStillThere_restoresExactly() {
        PositionRestorer.ExtendedTarget target =
                PositionRestorer.restoreExtended(
                        extended("c", 12, "b", 1, -40), Arrays.asList("a", "b", "c"));

        assertThat(target.match).isEqualTo(PositionRestorer.Match.EXACT);
        assertThat(target.caretBlockId).isEqualTo("c");
        assertThat(target.caretOffset).isEqualTo(12);
        assertThat(target.topBlockId).isEqualTo("b");
        assertThat(target.topOffsetPx).isEqualTo(-40);
    }

    @Test
    public void extended_blocksMoved_areFoundById() {
        PositionRestorer.ExtendedTarget target =
                PositionRestorer.restoreExtended(
                        extended("c", 3, "b", 1, 0), Arrays.asList("new", "x", "y", "b", "c"));

        assertThat(target.topBlockId).isEqualTo("b");
        assertThat(target.topBlockIndex).isEqualTo(3);
    }

    @Test
    public void extended_topBlockDeleted_viewportFollowsTheCaret() {
        PositionRestorer.ExtendedTarget target =
                PositionRestorer.restoreExtended(
                        extended("c", 3, "gone", 1, 0), Arrays.asList("a", "c"));

        assertThat(target.match).isEqualTo(PositionRestorer.Match.ANCHOR);
        assertThat(target.caretBlockId).isEqualTo("c");
        assertThat(target.topBlockId).isNull();
    }

    @Test
    public void extended_allBlocksReplaced_keepsTheIndexClampedWithoutACaret() {
        PositionRestorer.ExtendedTarget target =
                PositionRestorer.restoreExtended(
                        extended("old1", 3, "old2", 9, 20), Arrays.asList("n1", "n2", "n3"));

        assertThat(target.match).isEqualTo(PositionRestorer.Match.CLAMPED);
        assertThat(target.caretBlockId).isNull();
        assertThat(target.topBlockIndex).isEqualTo(2);
    }

    @Test
    public void extended_emptyNote_opensAtTheTop() {
        assertThat(
                        PositionRestorer.restoreExtended(
                                        extended("a", 1, "a", 0, 0), Collections.emptyList())
                                .match)
                .isEqualTo(PositionRestorer.Match.TOP);
    }

    @Test
    public void extended_titleOnScreenAndReading_opensAtTheTop() {
        assertThat(
                        PositionRestorer.restoreExtended(
                                        extended(null, 0, null, -1, 0), Arrays.asList("a"))
                                .match)
                .isEqualTo(PositionRestorer.Match.TOP);
    }

    @Test
    public void extended_negativeOffsetsAreClamped() {
        NoteViewState.Extended saved = extended("a", -7, null, -1, 0);
        saved.caretInput = -2;

        PositionRestorer.ExtendedTarget target =
                PositionRestorer.restoreExtended(saved, Arrays.asList("a"));

        assertThat(target.caretOffset).isEqualTo(0);
        assertThat(target.caretInput).isEqualTo(0);
    }
}
