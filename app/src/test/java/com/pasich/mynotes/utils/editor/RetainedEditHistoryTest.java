package com.pasich.mynotes.utils.editor;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class RetainedEditHistoryTest {

    @Test
    public void theHistoryIsTakenOnceForItsNote() {
        RetainedEditHistory retained = new RetainedEditHistory();
        retained.put(7, "{\"current\":{}}");

        assertThat(retained.take(7)).isEqualTo("{\"current\":{}}");
        assertThat(retained.take(7)).isNull();
    }

    @Test
    public void anotherNote_getsNothingAndTheHistoryIsForgotten() {
        RetainedEditHistory retained = new RetainedEditHistory();
        retained.put(7, "{}");

        assertThat(retained.take(8)).isNull();
        assertThat(retained.take(7)).isNull();
    }

    @Test
    public void aNoteWithoutAnId_getsNothing() {
        RetainedEditHistory retained = new RetainedEditHistory();
        retained.put(0, "{}");

        assertThat(retained.take(0)).isNull();
    }
}
