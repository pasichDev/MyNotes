package com.pasich.mynotes.utils.editor;

import static com.google.common.truth.Truth.assertThat;
import static com.pasich.mynotes.utils.editor.TextEditHistory.FIELD_BODY;
import static com.pasich.mynotes.utils.editor.TextEditHistory.FIELD_TITLE;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * The simple editor's undo history, driven the way the text fields drive it: every change reports
 * where it starts, what it removed and what it put in.
 */
public class TextEditHistoryTest {

    private static final long WINDOW = TextEditHistory.DEFAULT_MERGE_WINDOW_MS;

    private TextEditHistory history;
    private String title;
    private String body;
    private long now;

    @Before
    public void setUp() {
        history = new TextEditHistory();
        title = "";
        body = "";
        now = 1_000;
    }

    // -- helpers that edit the "fields" and record like the watchers do --------------------------

    private void replace(int field, int start, int end, String text, long at) {
        String current = field == FIELD_TITLE ? title : body;
        String removed = current.substring(start, end);
        String updated = current.substring(0, start) + text + current.substring(end);
        if (field == FIELD_TITLE) title = updated;
        else body = updated;
        history.record(field, start, removed, text, start, end, at);
    }

    private void type(String text) {
        for (char c : text.toCharArray()) {
            now += 50;
            replace(FIELD_BODY, body.length(), body.length(), String.valueOf(c), now);
        }
    }

    private void backspace(int times) {
        for (int i = 0; i < times; i++) {
            now += 50;
            replace(FIELD_BODY, body.length() - 1, body.length(), "", now);
        }
    }

    private TextEditHistory.Change undo() {
        return apply(history.undo());
    }

    private TextEditHistory.Change redo() {
        return apply(history.redo());
    }

    private TextEditHistory.Change apply(TextEditHistory.Change change) {
        if (change == null) return null;
        if (change.field == FIELD_TITLE) title = change.applyTo(title);
        else body = change.applyTo(body);
        return change;
    }

    // -- grouping -----------------------------------------------------------------------------

    @Test
    public void emptyHistoryHasNothingToUndoOrRedo() {
        assertThat(history.canUndo()).isFalse();
        assertThat(history.canRedo()).isFalse();
        assertThat(history.undo()).isNull();
        assertThat(history.redo()).isNull();
    }

    @Test
    public void lettersTypedInARowAreOneStep() {
        type("hello");

        undo();

        assertThat(body).isEmpty();
        assertThat(history.canUndo()).isFalse();
    }

    @Test
    public void eachWordIsItsOwnStepWithTheSpaceAfterIt() {
        type("hello world again");

        undo();
        assertThat(body).isEqualTo("hello world ");
        undo();
        assertThat(body).isEqualTo("hello ");
        undo();
        assertThat(body).isEmpty();
    }

    @Test
    public void aPauseStartsANewStep() {
        type("abc");
        now += WINDOW + 1;
        type("def");

        undo();

        assertThat(body).isEqualTo("abc");
    }

    @Test
    public void keyboardRewritingTheComposedWordJoinsTheStep() {
        // A keyboard composing a word replaces the whole word on every key.
        now += 50;
        replace(FIELD_BODY, 0, 0, "h", now);
        now += 50;
        replace(FIELD_BODY, 0, 1, "he", now);
        now += 50;
        replace(FIELD_BODY, 0, 2, "hel", now);
        now += 50;
        replace(FIELD_BODY, 0, 3, "help", now);

        undo();

        assertThat(body).isEmpty();
        assertThat(history.canUndo()).isFalse();
    }

    @Test
    public void correctingATypoWithBackspaceStaysInTheWord() {
        type("helo");
        backspace(1);
        type("lo");

        assertThat(body).isEqualTo("hello");
        undo();
        assertThat(body).isEmpty();
    }

    @Test
    public void typedAndBackspacedAwayLeavesNothingToUndo() {
        type("ab");
        backspace(2);

        assertThat(history.canUndo()).isFalse();
    }

    @Test
    public void backspacingThroughOlderTextIsOneStepPerWord() {
        body = "one two";
        now += WINDOW + 1;
        backspace(4); // "owt " -> "one"

        undo();
        assertThat(body).isEqualTo("one ");
        undo();
        assertThat(body).isEqualTo("one two");
    }

    @Test
    public void forwardDeleteJoinsTheStep() {
        body = "abcdef";
        now += 50;
        replace(FIELD_BODY, 1, 2, "", now);
        now += 50;
        replace(FIELD_BODY, 1, 2, "", now);

        assertThat(body).isEqualTo("adef");
        undo();
        assertThat(body).isEqualTo("abcdef");
    }

    @Test
    public void typingAfterADeletionIsASeparateStep() {
        body = "abc";
        now += 50;
        replace(FIELD_BODY, 2, 3, "", now);
        now += 50;
        replace(FIELD_BODY, 2, 2, "x", now);

        undo();
        assertThat(body).isEqualTo("ab");
        undo();
        assertThat(body).isEqualTo("abc");
    }

    @Test
    public void aPasteIsAStepOfItsOwn() {
        type("note");
        now += 50;
        replace(FIELD_BODY, 4, 4, " pasted text", now);

        undo();

        assertThat(body).isEqualTo("note");
    }

    @Test
    public void titleAndBodyNeverShareAStep() {
        now += 50;
        replace(FIELD_TITLE, 0, 0, "T", now);
        type("b");

        TextEditHistory.Change change = undo();

        assertThat(change.field).isEqualTo(FIELD_BODY);
        assertThat(title).isEqualTo("T");
        assertThat(body).isEmpty();
        change = undo();
        assertThat(change.field).isEqualTo(FIELD_TITLE);
        assertThat(title).isEmpty();
    }

    @Test
    public void sealEndsTheStepBeingTyped() {
        type("ab");
        history.seal();
        type("cd");

        undo();

        assertThat(body).isEqualTo("ab");
    }

    // -- redo -----------------------------------------------------------------------------------

    @Test
    public void undoneStepsCanBeRedoneInOrder() {
        type("one two three");
        undo();
        undo();
        assertThat(body).isEqualTo("one ");

        redo();
        assertThat(body).isEqualTo("one two ");
        redo();
        assertThat(body).isEqualTo("one two three");
        assertThat(history.canRedo()).isFalse();
    }

    @Test
    public void aNewEditAfterAnUndoMakesRedoUnavailable() {
        type("one two");
        undo();
        assertThat(history.canRedo()).isTrue();

        type("x");

        assertThat(history.canRedo()).isFalse();
        assertThat(history.redo()).isNull();
        assertThat(body).isEqualTo("one x");
    }

    @Test
    public void typingAfterAnUndoDoesNotJoinTheUndoneStep() {
        type("ab");
        now += WINDOW + 1;
        type("cd");
        undo();

        type("x");
        undo();

        assertThat(body).isEqualTo("ab");
    }

    // -- selection --------------------------------------------------------------------------------

    @Test
    public void undoPutsBackTheSelectionThatWasReplaced() {
        body = "keep this word";
        now += WINDOW + 1;
        // Select "this" (5..9) and type over it.
        replace(FIELD_BODY, 5, 9, "t", now);
        for (char c : "hat".toCharArray()) {
            now += 50;
            replace(
                    FIELD_BODY,
                    body.indexOf(" word"),
                    body.indexOf(" word"),
                    String.valueOf(c),
                    now);
        }
        assertThat(body).isEqualTo("keep that word");

        TextEditHistory.Change change = undo();

        assertThat(body).isEqualTo("keep this word");
        assertThat(change.selectionStart).isEqualTo(5);
        assertThat(change.selectionEnd).isEqualTo(9);
    }

    @Test
    public void redoPutsTheCaretAfterTheRedoneText() {
        type("abc");
        undo();

        TextEditHistory.Change change = redo();

        assertThat(body).isEqualTo("abc");
        assertThat(change.selectionStart).isEqualTo(3);
        assertThat(change.selectionEnd).isEqualTo(3);
    }

    // -- limits ---------------------------------------------------------------------------------

    @Test
    public void oldestStepsAreDroppedBeyondTheStepLimit() {
        history = new TextEditHistory(3, 1_000, WINDOW);
        for (int i = 0; i < 5; i++) {
            now += WINDOW + 1;
            replace(FIELD_BODY, body.length(), body.length(), String.valueOf(i), now);
        }

        int undone = 0;
        while (undo() != null) undone++;

        assertThat(undone).isEqualTo(3);
        assertThat(body).isEqualTo("01");
    }

    @Test
    public void oldestStepsAreDroppedBeyondTheCharacterLimit() {
        history = new TextEditHistory(100, 10, WINDOW);
        now += WINDOW + 1;
        replace(FIELD_BODY, 0, 0, "aaaaaa", now);
        now += WINDOW + 1;
        replace(FIELD_BODY, 6, 6, "bbbbbb", now);

        undo();

        assertThat(body).isEqualTo("aaaaaa");
        assertThat(history.canUndo()).isFalse();
    }

    @Test
    public void theNewestStepIsKeptEvenWhenItAloneIsOverTheLimit() {
        history = new TextEditHistory(100, 4, WINDOW);
        now += 50;
        replace(FIELD_BODY, 0, 0, "longer than four", now);

        assertThat(history.canUndo()).isTrue();
    }

    @Test
    public void clearForgetsEverything() {
        type("one two");
        undo();

        history.clear();

        assertThat(history.canUndo()).isFalse();
        assertThat(history.canRedo()).isFalse();
    }

    // -- listener -------------------------------------------------------------------------------

    @Test
    public void listenerHearsOnlyWhenAvailabilityChanges() {
        List<String> states = new ArrayList<>();
        history.setListener((canUndo, canRedo) -> states.add(canUndo + "," + canRedo));

        type("abc");
        undo();
        redo();

        assertThat(states)
                .containsExactly("false,false", "true,false", "false,true", "true,false")
                .inOrder();
    }

    // -- saved state ------------------------------------------------------------------------------

    @Test
    public void snapshotRestoresIntoTheSameText() {
        type("one two three");
        undo();
        TextEditHistory.Snapshot snapshot = history.snapshot(10_000, title, body);

        TextEditHistory restored = new TextEditHistory();
        assertThat(restored.restore(snapshot, title, body)).isTrue();
        history = restored;

        redo();
        assertThat(body).isEqualTo("one two three");
        undo();
        undo();
        assertThat(body).isEqualTo("one ");
    }

    @Test
    public void snapshotIsRefusedForDifferentText() {
        type("one two");
        TextEditHistory.Snapshot snapshot = history.snapshot(10_000, title, body);

        TextEditHistory restored = new TextEditHistory();

        assertThat(restored.restore(snapshot, title, "something else")).isFalse();
        assertThat(restored.canUndo()).isFalse();
    }

    @Test
    public void snapshotKeepsTheMostRecentStepsWithinItsBudget() {
        type("aaaa bbbb cccc");
        TextEditHistory.Snapshot snapshot = history.snapshot(10, title, body);

        assertThat(snapshot.fields.length).isEqualTo(2);
        TextEditHistory restored = new TextEditHistory();
        restored.restore(snapshot, title, body);
        history = restored;
        undo();
        undo();
        assertThat(body).isEqualTo("aaaa ");
        assertThat(history.canUndo()).isFalse();
    }

    @Test
    public void restoredStepsDoNotJoinNewTyping() {
        type("ab");
        TextEditHistory.Snapshot snapshot = history.snapshot(10_000, title, body);
        TextEditHistory restored = new TextEditHistory();
        restored.restore(snapshot, title, body);
        history = restored;

        type("c");
        undo();

        assertThat(body).isEqualTo("ab");
    }
}
