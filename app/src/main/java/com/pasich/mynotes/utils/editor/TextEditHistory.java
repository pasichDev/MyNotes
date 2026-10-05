package com.pasich.mynotes.utils.editor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Undo and redo for the simple editor, kept free of Android types so it is covered by plain JVM
 * tests.
 *
 * <p>Every change to a field is recorded as a diff: where it starts, the text it removed and the
 * text it put in, with the selection before and after it. Typing is grouped into steps the way
 * people think about it: characters typed in a row, a keyboard rewriting the word it is composing
 * and backspacing through text all join the step before them while they follow it closely in time
 * and place, and a new word starts a new step. A step only ever touches one field, so an undo never
 * changes the title and the body at once.
 *
 * <p>The history is bounded in steps and in characters; the oldest steps are dropped first.
 */
public final class TextEditHistory {

    /** Field ids; a step belongs to exactly one field. */
    public static final int FIELD_TITLE = 0;

    public static final int FIELD_BODY = 1;

    /** Default limits: enough for a long editing session, small enough to never matter. */
    public static final int DEFAULT_MAX_STEPS = 200;

    public static final int DEFAULT_MAX_CHARS = 256 * 1024;
    public static final long DEFAULT_MERGE_WINDOW_MS = 1500;

    /** Told when undo or redo becomes available or unavailable. */
    public interface Listener {
        void onHistoryStateChanged(boolean canUndo, boolean canRedo);
    }

    /** One undoable change. Immutable once it has left the top of the undo stack. */
    public static final class Step {
        public final int field;
        public final int start;
        public final String removed;
        public final String inserted;
        public final int selectionBeforeStart;
        public final int selectionBeforeEnd;
        public final int selectionAfterStart;
        public final int selectionAfterEnd;
        final long time;
        final boolean sealed;

        Step(
                int field,
                int start,
                String removed,
                String inserted,
                int selectionBeforeStart,
                int selectionBeforeEnd,
                int selectionAfterStart,
                int selectionAfterEnd,
                long time,
                boolean sealed) {
            this.field = field;
            this.start = start;
            this.removed = removed;
            this.inserted = inserted;
            this.selectionBeforeStart = selectionBeforeStart;
            this.selectionBeforeEnd = selectionBeforeEnd;
            this.selectionAfterStart = selectionAfterStart;
            this.selectionAfterEnd = selectionAfterEnd;
            this.time = time;
            this.sealed = sealed;
        }

        Step sealed() {
            if (sealed) return this;
            return new Step(
                    field,
                    start,
                    removed,
                    inserted,
                    selectionBeforeStart,
                    selectionBeforeEnd,
                    selectionAfterStart,
                    selectionAfterEnd,
                    time,
                    true);
        }

        int size() {
            return removed.length() + inserted.length();
        }
    }

    /**
     * What applying an undo or redo means for a field: replace {@code [start, start +
     * replaceLength)} with {@code text}, then select {@code [selectionStart, selectionEnd]}.
     */
    public static final class Change {
        public final int field;
        public final int start;
        public final int replaceLength;
        public final String text;
        public final int selectionStart;
        public final int selectionEnd;

        Change(
                int field,
                int start,
                int replaceLength,
                String text,
                int selectionStart,
                int selectionEnd) {
            this.field = field;
            this.start = start;
            this.replaceLength = replaceLength;
            this.text = text;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
        }

        /** The field's text after this change, for callers that hold it as a string. */
        public String applyTo(String current) {
            return current.substring(0, start) + text + current.substring(start + replaceLength);
        }
    }

    /**
     * The history in a form that fits a saved-instance state: parallel arrays, oldest step first.
     * {@code undoCount} steps can be undone; the rest can be redone, the next redo last.
     */
    public static final class Snapshot {
        public final int[] fields;
        public final int[] starts;
        public final String[] removed;
        public final String[] inserted;
        // before start, before end, after start, after end, per step.
        public final int[] selections;
        public final int undoCount;
        // Which texts the history ends at; a restore into anything else is refused.
        public final int titleHash;
        public final int bodyHash;

        public Snapshot(
                int[] fields,
                int[] starts,
                String[] removed,
                String[] inserted,
                int[] selections,
                int undoCount,
                int titleHash,
                int bodyHash) {
            this.fields = fields;
            this.starts = starts;
            this.removed = removed;
            this.inserted = inserted;
            this.selections = selections;
            this.undoCount = undoCount;
            this.titleHash = titleHash;
            this.bodyHash = bodyHash;
        }

        boolean isConsistent() {
            int n = fields == null ? -1 : fields.length;
            return n >= 0
                    && starts != null
                    && starts.length == n
                    && removed != null
                    && removed.length == n
                    && inserted != null
                    && inserted.length == n
                    && selections != null
                    && selections.length == n * 4
                    && undoCount >= 0
                    && undoCount <= n;
        }
    }

    private final int maxSteps;
    private final int maxChars;
    private final long mergeWindowMs;
    private final Deque<Step> undo = new ArrayDeque<>();
    private final Deque<Step> redo = new ArrayDeque<>();
    private int totalChars = 0;
    private int redoChars = 0;
    @Nullable private Listener listener;
    private boolean lastCanUndo = false;
    private boolean lastCanRedo = false;

    public TextEditHistory() {
        this(DEFAULT_MAX_STEPS, DEFAULT_MAX_CHARS, DEFAULT_MERGE_WINDOW_MS);
    }

    public TextEditHistory(int maxSteps, int maxChars, long mergeWindowMs) {
        this.maxSteps = Math.max(1, maxSteps);
        this.maxChars = Math.max(1, maxChars);
        this.mergeWindowMs = mergeWindowMs;
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
        lastCanUndo = canUndo();
        lastCanRedo = canRedo();
        if (listener != null) listener.onHistoryStateChanged(lastCanUndo, lastCanRedo);
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /**
     * Records a change the user made to {@code field}: {@code removed} at {@code start} was
     * replaced by {@code inserted}, with {@code [selectionStart, selectionEnd]} selected before it.
     * A new change makes everything that could be redone unreachable, so redo is cleared.
     */
    public void record(
            int field,
            int start,
            @NonNull String removed,
            @NonNull String inserted,
            int selectionStart,
            int selectionEnd,
            long now) {
        if (removed.equals(inserted)) return;
        redo.clear();
        int caret = start + inserted.length();
        Step top = undo.peekLast();
        Step merged = top == null ? null : merge(top, field, start, removed, inserted, caret, now);
        if (merged != null) {
            undo.pollLast();
            totalChars -= top.size();
            // Typed and then backspaced away again: nothing is left to undo.
            if (!merged.removed.isEmpty() || !merged.inserted.isEmpty()) push(merged);
        } else {
            if (top != null && !top.sealed) {
                undo.pollLast();
                undo.addLast(top.sealed());
            }
            push(
                    new Step(
                            field,
                            start,
                            removed,
                            inserted,
                            selectionStart,
                            selectionEnd,
                            caret,
                            caret,
                            now,
                            false));
        }
        recountRedo();
        trim();
        notifyIfChanged();
    }

    /** Closes the step being typed, so the next change starts a new one. */
    public void seal() {
        Step top = undo.peekLast();
        if (top != null && !top.sealed) {
            undo.pollLast();
            undo.addLast(top.sealed());
        }
    }

    /** Takes back the last step, or returns null when there is nothing to undo. */
    @Nullable
    public Change undo() {
        Step step = undo.pollLast();
        if (step == null) return null;
        step = step.sealed();
        redo.addLast(step);
        notifyIfChanged();
        return new Change(
                step.field,
                step.start,
                step.inserted.length(),
                step.removed,
                step.selectionBeforeStart,
                step.selectionBeforeEnd);
    }

    /** Applies the last undone step again, or returns null when there is nothing to redo. */
    @Nullable
    public Change redo() {
        Step step = redo.pollLast();
        if (step == null) return null;
        undo.addLast(step);
        notifyIfChanged();
        return new Change(
                step.field,
                step.start,
                step.removed.length(),
                step.inserted,
                step.selectionAfterStart,
                step.selectionAfterEnd);
    }

    /** Forgets everything; used when a different note, or another version of it, is shown. */
    public void clear() {
        undo.clear();
        redo.clear();
        totalChars = 0;
        notifyIfChanged();
    }

    /**
     * The most recent steps whose text fits in {@code maxChars}, for the saved-instance state.
     * Steps nearest the current text are kept: the newest undo steps and the next redo steps.
     */
    @NonNull
    public Snapshot snapshot(int maxChars, @NonNull String title, @NonNull String body) {
        List<Step> undoKept = new ArrayList<>();
        List<Step> redoKept = new ArrayList<>();
        int budget = Math.max(0, maxChars);
        Iterator<Step> undoIt = undo.descendingIterator();
        Iterator<Step> redoIt = redo.descendingIterator();
        boolean undoOpen = true;
        boolean redoOpen = true;
        // Alternate so neither direction is starved by the other.
        while (undoOpen || redoOpen) {
            if (undoOpen) {
                if (undoIt.hasNext()) {
                    Step s = undoIt.next();
                    if (s.size() <= budget) {
                        budget -= s.size();
                        undoKept.add(0, s);
                    } else undoOpen = false;
                } else undoOpen = false;
            }
            if (redoOpen) {
                if (redoIt.hasNext()) {
                    Step s = redoIt.next();
                    if (s.size() <= budget) {
                        budget -= s.size();
                        redoKept.add(s);
                    } else redoOpen = false;
                } else redoOpen = false;
            }
        }
        // Redo steps are stored oldest first after the undo steps: the next redo comes last.
        List<Step> all = new ArrayList<>(undoKept);
        for (int i = redoKept.size() - 1; i >= 0; i--) all.add(redoKept.get(i));
        int n = all.size();
        int[] fields = new int[n];
        int[] starts = new int[n];
        String[] removed = new String[n];
        String[] inserted = new String[n];
        int[] selections = new int[n * 4];
        for (int i = 0; i < n; i++) {
            Step s = all.get(i);
            fields[i] = s.field;
            starts[i] = s.start;
            removed[i] = s.removed;
            inserted[i] = s.inserted;
            selections[i * 4] = s.selectionBeforeStart;
            selections[i * 4 + 1] = s.selectionBeforeEnd;
            selections[i * 4 + 2] = s.selectionAfterStart;
            selections[i * 4 + 3] = s.selectionAfterEnd;
        }
        return new Snapshot(
                fields,
                starts,
                removed,
                inserted,
                selections,
                undoKept.size(),
                title.hashCode(),
                body.hashCode());
    }

    /**
     * Replaces the history with a saved one, if it was saved for exactly these texts.
     *
     * @return whether it was restored; otherwise the history is left empty.
     */
    public boolean restore(
            @Nullable Snapshot snapshot, @NonNull String title, @NonNull String body) {
        undo.clear();
        redo.clear();
        totalChars = 0;
        boolean ok =
                snapshot != null
                        && snapshot.isConsistent()
                        && snapshot.titleHash == title.hashCode()
                        && snapshot.bodyHash == body.hashCode();
        if (ok) {
            int n = snapshot.fields.length;
            for (int i = 0; i < n; i++) {
                if (snapshot.removed[i] == null || snapshot.inserted[i] == null) {
                    undo.clear();
                    redo.clear();
                    ok = false;
                    break;
                }
                Step s =
                        new Step(
                                snapshot.fields[i],
                                snapshot.starts[i],
                                snapshot.removed[i],
                                snapshot.inserted[i],
                                snapshot.selections[i * 4],
                                snapshot.selections[i * 4 + 1],
                                snapshot.selections[i * 4 + 2],
                                snapshot.selections[i * 4 + 3],
                                0L,
                                true);
                if (i < snapshot.undoCount) undo.addLast(s);
                else redo.addLast(s);
            }
        }
        totalChars = 0;
        for (Step s : undo) totalChars += s.size();
        recountRedo();
        notifyIfChanged();
        return ok;
    }

    // -- internals ------------------------------------------------------------------------------

    private void push(Step step) {
        undo.addLast(step);
        totalChars += step.size();
    }

    private void recountRedo() {
        redoChars = 0;
        for (Step s : redo) redoChars += s.size();
    }

    /** Drops the oldest steps until the history is within its limits; the newest always stays. */
    private void trim() {
        while (undo.size() > 1 && (undo.size() > maxSteps || totalChars + redoChars > maxChars)) {
            Step dropped = undo.pollFirst();
            if (dropped != null) totalChars -= dropped.size();
        }
    }

    /**
     * Joins a change to the open step before it when it continues it, or returns null.
     *
     * <ul>
     *   <li>Inside or at the end of what the step put in: typing on, a keyboard rewriting the word
     *       it is composing, a typo corrected with backspace. Appending after whitespace starts a
     *       new word and so a new step.
     *   <li>Backspace or forward delete right next to what the step removed.
     * </ul>
     */
    @Nullable
    private Step merge(
            Step top, int field, int start, String removed, String inserted, int caret, long now) {
        if (top.sealed || top.field != field || now - top.time > mergeWindowMs || now < top.time) {
            return null;
        }
        int topInsertedEnd = top.start + top.inserted.length();

        // Within (or right at the end of) the text this step inserted. Typing after a deletion
        // is a step of its own, so an undo does not take both back at once.
        if (!top.inserted.isEmpty()
                && start >= top.start
                && start + removed.length() <= topInsertedEnd) {
            boolean append = start == topInsertedEnd && removed.isEmpty();
            if (append && startsNewWord(top.inserted, inserted)) return null;
            if (append && inserted.length() > 1) {
                // A paste or a whole suggested word is a step of its own.
                return null;
            }
            int from = start - top.start;
            String combined =
                    top.inserted.substring(0, from)
                            + inserted
                            + top.inserted.substring(from + removed.length());
            return new Step(
                    field,
                    top.start,
                    top.removed,
                    combined,
                    top.selectionBeforeStart,
                    top.selectionBeforeEnd,
                    caret,
                    caret,
                    now,
                    false);
        }

        // Deleting on past what this step already removed.
        if (inserted.isEmpty() && top.inserted.isEmpty() && !removed.isEmpty()) {
            if (start + removed.length() == top.start) {
                // Backspace.
                if (startsNewWordBackwards(removed, top.removed)) return null;
                return new Step(
                        field,
                        start,
                        removed + top.removed,
                        "",
                        top.selectionBeforeStart,
                        top.selectionBeforeEnd,
                        caret,
                        caret,
                        now,
                        false);
            }
            if (start == top.start) {
                // Forward delete.
                return new Step(
                        field,
                        top.start,
                        top.removed + removed,
                        "",
                        top.selectionBeforeStart,
                        top.selectionBeforeEnd,
                        caret,
                        caret,
                        now,
                        false);
            }
        }
        return null;
    }

    /** Typing a letter after whitespace begins a new word. */
    private static boolean startsNewWord(String before, String next) {
        if (before.isEmpty() || next.isEmpty()) return false;
        return Character.isWhitespace(before.charAt(before.length() - 1))
                && !Character.isWhitespace(next.charAt(0));
    }

    /** Backspacing into whitespace after deleting a word ends that word's step. */
    private static boolean startsNewWordBackwards(String removedNow, String removedBefore) {
        if (removedNow.isEmpty() || removedBefore.isEmpty()) return false;
        return Character.isWhitespace(removedNow.charAt(removedNow.length() - 1))
                && !Character.isWhitespace(removedBefore.charAt(0));
    }

    private void notifyIfChanged() {
        boolean u = canUndo();
        boolean r = canRedo();
        if (u == lastCanUndo && r == lastCanRedo) return;
        lastCanUndo = u;
        lastCanRedo = r;
        if (listener != null) listener.onHistoryStateChanged(u, r);
    }
}
