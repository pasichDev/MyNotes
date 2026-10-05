package com.pasich.mynotes.utils.editor;

import static com.google.common.truth.Truth.assertThat;

import com.pasich.mynotes.utils.editor.KeyboardRequest.Step;
import org.junit.Test;

public class KeyboardRequestTest {

    @Test
    public void withoutARequest_nothingHappens() {
        assertThat(new KeyboardRequest().next(false, true, true)).isEqualTo(Step.DONE);
    }

    @Test
    public void aRequestBeforeTheWindowHasFocus_waitsForIt() {
        KeyboardRequest request = new KeyboardRequest();
        request.start();

        assertThat(request.next(false, false, true)).isEqualTo(Step.WAIT_FOR_WINDOW_FOCUS);
        assertThat(request.isPending()).isTrue();
        assertThat(request.next(false, true, true)).isEqualTo(Step.CONNECT_AND_SHOW);
    }

    @Test
    public void aDroppedRequest_isRepeatedUntilTheKeyboardIsUp() {
        KeyboardRequest request = new KeyboardRequest();
        request.start();

        assertThat(request.next(false, true, true)).isEqualTo(Step.CONNECT_AND_SHOW);
        assertThat(request.next(false, true, true)).isEqualTo(Step.SHOW_AND_RETRY);
        assertThat(request.next(true, true, true)).isEqualTo(Step.DONE);
        assertThat(request.isPending()).isFalse();
    }

    @Test
    public void aKeyboardUpBeforeTheEditableHasFocus_isConnectedOnceItHas() {
        KeyboardRequest request = new KeyboardRequest();
        request.start();

        // The keyboard is on screen but the page's field has no focus: it is attached to nothing,
        // so it does not count, and nothing is asked until the field has the focus.
        assertThat(request.next(true, true, false)).isEqualTo(Step.FOCUS_AND_RETRY);
        assertThat(request.next(true, true, false)).isEqualTo(Step.FOCUS_AND_RETRY);
        assertThat(request.next(true, true, true)).isEqualTo(Step.CONNECT_AND_SHOW);
        assertThat(request.next(true, true, true)).isEqualTo(Step.DONE);
    }

    @Test
    public void anEditableThatLosesFocus_isConnectedAgain() {
        KeyboardRequest request = new KeyboardRequest();
        request.start();

        assertThat(request.next(false, true, true)).isEqualTo(Step.CONNECT_AND_SHOW);
        assertThat(request.next(false, true, false)).isEqualTo(Step.FOCUS_AND_RETRY);
        assertThat(request.next(false, true, true)).isEqualTo(Step.CONNECT_AND_SHOW);
    }

    @Test
    public void aKeyboardThatNeverComes_isGivenUpOn() {
        KeyboardRequest request = new KeyboardRequest();
        request.start();

        assertThat(request.next(false, true, true)).isEqualTo(Step.CONNECT_AND_SHOW);
        for (int i = 1; i < KeyboardRequest.ATTEMPTS; i++) {
            assertThat(request.next(false, true, true)).isEqualTo(Step.SHOW_AND_RETRY);
        }
        assertThat(request.next(false, true, true)).isEqualTo(Step.DONE);
    }

    @Test
    public void aCancelledRequest_stops() {
        KeyboardRequest request = new KeyboardRequest();
        request.start();
        request.cancel();

        assertThat(request.next(false, true, true)).isEqualTo(Step.DONE);
    }
}
