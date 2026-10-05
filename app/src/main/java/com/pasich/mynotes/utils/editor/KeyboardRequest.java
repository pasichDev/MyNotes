package com.pasich.mynotes.utils.editor;

/**
 * Brings up the on-screen keyboard for a view that cannot take it at once, kept free of Android
 * types so it is covered by plain JVM tests.
 *
 * <p>A request made while the window has no focus yet (the screen is still opening) or before the
 * focused view is connected to the input method (a WebView whose page has just placed its caret) is
 * dropped by the system without a word. Worse, a keyboard asked for before the page's editable
 * field has focus can come up attached to nothing: it is on screen, but there is no caret and
 * typing goes nowhere. So a request waits for window focus, then for the editable field to hold the
 * focus, then connects the keyboard to it afresh, and is repeated a few times until the keyboard is
 * actually visible.
 */
public final class KeyboardRequest {

    /** What to do next. */
    public enum Step {
        /** Nothing is asked, or the keyboard is up on the editable field: stop. */
        DONE,
        /** The window has no focus: try again when it gets it. */
        WAIT_FOR_WINDOW_FOCUS,
        /**
         * The editable field has no focus yet: focus the view and its caret, ask for nothing and
         * check again after {@link #RETRY_MS}.
         */
        FOCUS_AND_RETRY,
        /**
         * The editable field has just got the focus: connect the input method to it anew (a
         * keyboard already on screen may still be attached to nothing), ask for the keyboard and
         * check again after {@link #RETRY_MS}.
         */
        CONNECT_AND_SHOW,
        /** Ask for the keyboard and check again after {@link #RETRY_MS}. */
        SHOW_AND_RETRY
    }

    public static final int ATTEMPTS = 20;
    public static final long RETRY_MS = 100;

    private int attemptsLeft;
    private boolean connected;

    /** Starts a new request; any earlier one is replaced. */
    public void start() {
        attemptsLeft = ATTEMPTS;
        connected = false;
    }

    /** Drops the request, for example when the note leaves editing mode. */
    public void cancel() {
        attemptsLeft = 0;
    }

    public boolean isPending() {
        return attemptsLeft > 0;
    }

    /**
     * The next step, given whether the keyboard is visible, the window has focus and the editable
     * field in the view holds the focus.
     */
    public Step next(boolean keyboardVisible, boolean windowFocused, boolean editorFocused) {
        if (attemptsLeft <= 0) return Step.DONE;
        if (!windowFocused) return Step.WAIT_FOR_WINDOW_FOCUS;
        attemptsLeft--;
        if (!editorFocused) {
            connected = false;
            return Step.FOCUS_AND_RETRY;
        }
        if (!connected) {
            connected = true;
            return Step.CONNECT_AND_SHOW;
        }
        if (keyboardVisible) {
            attemptsLeft = 0;
            return Step.DONE;
        }
        return Step.SHOW_AND_RETRY;
    }
}
