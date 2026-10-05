package com.pasich.mynotes.utils.editor;

/**
 * Brings up the on-screen keyboard for a view that cannot take it at once, kept free of Android
 * types so it is covered by plain JVM tests.
 *
 * <p>A request made while the window has no focus yet (the screen is still opening) or before the
 * focused view is connected to the input method (a WebView whose page has just placed its caret) is
 * dropped by the system without a word. So a request waits for window focus and is then repeated a
 * few times until the keyboard is actually visible.
 */
public final class KeyboardRequest {

    /** What to do next. */
    public enum Step {
        /** Nothing is asked, or the keyboard is up: stop. */
        DONE,
        /** The window has no focus: try again when it gets it. */
        WAIT_FOR_WINDOW_FOCUS,
        /** Focus the view, ask for the keyboard and check again after {@link #RETRY_MS}. */
        SHOW_AND_RETRY
    }

    public static final int ATTEMPTS = 10;
    public static final long RETRY_MS = 100;

    private int attemptsLeft;

    /** Starts a new request; any earlier one is replaced. */
    public void start() {
        attemptsLeft = ATTEMPTS;
    }

    /** Drops the request, for example when the note leaves editing mode. */
    public void cancel() {
        attemptsLeft = 0;
    }

    public boolean isPending() {
        return attemptsLeft > 0;
    }

    /** The next step, given whether the keyboard is visible and the window has focus. */
    public Step next(boolean keyboardVisible, boolean windowFocused) {
        if (attemptsLeft <= 0) return Step.DONE;
        if (keyboardVisible) {
            attemptsLeft = 0;
            return Step.DONE;
        }
        if (!windowFocused) return Step.WAIT_FOR_WINDOW_FOCUS;
        attemptsLeft--;
        return Step.SHOW_AND_RETRY;
    }
}
