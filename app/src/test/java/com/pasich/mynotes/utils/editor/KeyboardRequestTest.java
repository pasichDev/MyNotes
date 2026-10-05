package com.pasich.mynotes.utils.editor;

import static com.google.common.truth.Truth.assertThat;

import com.pasich.mynotes.utils.editor.KeyboardRequest.Step;
import org.junit.Test;

public class KeyboardRequestTest {

    @Test
    public void withoutARequest_nothingHappens() {
        assertThat(new KeyboardRequest().next(false, true)).isEqualTo(Step.DONE);
    }

    @Test
    public void aRequestBeforeTheWindowHasFocus_waitsForIt() {
        KeyboardRequest request = new KeyboardRequest();
        request.start();

        assertThat(request.next(false, false)).isEqualTo(Step.WAIT_FOR_WINDOW_FOCUS);
        assertThat(request.isPending()).isTrue();
        assertThat(request.next(false, true)).isEqualTo(Step.SHOW_AND_RETRY);
    }

    @Test
    public void aDroppedRequest_isRepeatedUntilTheKeyboardIsUp() {
        KeyboardRequest request = new KeyboardRequest();
        request.start();

        assertThat(request.next(false, true)).isEqualTo(Step.SHOW_AND_RETRY);
        assertThat(request.next(false, true)).isEqualTo(Step.SHOW_AND_RETRY);
        assertThat(request.next(true, true)).isEqualTo(Step.DONE);
        assertThat(request.isPending()).isFalse();
    }

    @Test
    public void aKeyboardThatNeverComes_isGivenUpOn() {
        KeyboardRequest request = new KeyboardRequest();
        request.start();

        for (int i = 0; i < KeyboardRequest.ATTEMPTS; i++) {
            assertThat(request.next(false, true)).isEqualTo(Step.SHOW_AND_RETRY);
        }
        assertThat(request.next(false, true)).isEqualTo(Step.DONE);
    }

    @Test
    public void aCancelledRequest_stops() {
        KeyboardRequest request = new KeyboardRequest();
        request.start();
        request.cancel();

        assertThat(request.next(false, true)).isEqualTo(Step.DONE);
    }
}
