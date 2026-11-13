package com.pasich.mynotes.utils.editor;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RetainedNoteDraftTest {

    @Test
    public void theDraftIsTakenOnceForItsNote() {
        RetainedNoteDraft retained = new RetainedNoteDraft();
        retained.put(7, "title", "body");

        RetainedNoteDraft.Draft draft = retained.take(7);
        assertThat(draft.title).isEqualTo("title");
        assertThat(draft.body).isEqualTo("body");
        assertThat(retained.take(7)).isNull();
    }

    @Test
    public void aLaterDocumentReplacesTheBodyAndKeepsTheTitle() {
        RetainedNoteDraft retained = new RetainedNoteDraft();
        retained.put(7, "title", "[1]");
        retained.put(7, null, "[2]");

        RetainedNoteDraft.Draft draft = retained.take(7);
        assertThat(draft.title).isEqualTo("title");
        assertThat(draft.body).isEqualTo("[2]");
    }

    @Test
    public void anotherNote_getsNothing() {
        RetainedNoteDraft retained = new RetainedNoteDraft();
        retained.put(7, null, "[1]");

        assertThat(retained.take(8)).isNull();
        assertThat(retained.take(7)).isNull();
    }

    /**
     * After a rotation the new screen shows the note only once the old page has handed over its
     * last document, which arrives after the new screen has started.
     */
    @Test
    public void theNewScreenWaitsForTheOldPage() {
        RetainedNoteDraft retained = new RetainedNoteDraft();
        List<String> shown = new ArrayList<>();
        retained.previousPageFinishing();

        retained.whenPreviousPageFinished(() -> shown.add(retained.take(7).body));
        assertThat(shown).isEmpty();

        retained.put(7, null, "[last]");
        retained.previousPageFinished();
        assertThat(shown).containsExactly("[last]");
    }

    @Test
    public void withNoPageFinishingTheNoteIsShownAtOnce() {
        RetainedNoteDraft retained = new RetainedNoteDraft();
        List<String> shown = new ArrayList<>();

        retained.whenPreviousPageFinished(() -> shown.add("now"));

        assertThat(shown).containsExactly("now");
    }

    @Test
    public void twoQuickRecreationsWaitForBothPages() {
        RetainedNoteDraft retained = new RetainedNoteDraft();
        List<String> shown = new ArrayList<>();
        retained.previousPageFinishing();
        retained.previousPageFinishing();
        retained.whenPreviousPageFinished(() -> shown.add("now"));

        retained.previousPageFinished();
        assertThat(shown).isEmpty();
        retained.previousPageFinished();
        assertThat(shown).containsExactly("now");
    }
}
