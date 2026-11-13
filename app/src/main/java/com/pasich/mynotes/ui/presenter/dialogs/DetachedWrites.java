package com.pasich.mynotes.ui.presenter.dialogs;

import android.util.Log;
import io.reactivex.Completable;
import io.reactivex.Scheduler;

/**
 * Runs a database write that a dialog starts right before it closes.
 *
 * <p>A dialog's presenter disposes its subscriptions when the dialog is dismissed, which happens in
 * the same click that starts the write, so a write tied to them may be cancelled before the io
 * thread picks it up. These writes are short, so they run to the end on their own.
 */
final class DetachedWrites {

    private DetachedWrites() {}

    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void run(Completable write, Scheduler io, String tag, String what) {
        write.subscribeOn(io).subscribe(() -> {}, error -> Log.e(tag, what + " failed", error));
    }
}
