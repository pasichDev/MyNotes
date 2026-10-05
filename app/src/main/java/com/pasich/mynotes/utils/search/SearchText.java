package com.pasich.mynotes.utils.search;

import java.text.Normalizer;
import java.util.Arrays;

/**
 * Folds text into the form search compares: compatibility-decomposed (NFKD), diacritics removed,
 * lower-cased per code point with the locale-independent mapping, and every run of whitespace
 * collapsed into a single space with none at either end.
 *
 * <p>So "Caf\u00e9", "CAFE" and "cafe" all fold to {@code cafe}, and " file notes " to {@code file
 * notes}. Plain Java, no Android types, so it is unit-tested directly.
 */
public final class SearchText {

    private SearchText() {}

    /** Folds {@code text} for comparison. {@code null} folds to the empty string. */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) return "";
        String decomposed = isAscii(text) ? text : Normalizer.normalize(text, Normalizer.Form.NFKD);
        StringBuilder out = new StringBuilder(decomposed.length());
        boolean pendingSpace = false;
        for (int i = 0; i < decomposed.length(); ) {
            int cp = decomposed.codePointAt(i);
            i += Character.charCount(cp);
            pendingSpace = append(out, cp, pendingSpace, null, 0, 0);
        }
        return out.toString();
    }

    /**
     * Finds the first place where already-normalized {@code query} occurs in {@code original} once
     * that is folded, and returns it as a {@code [start, end)} range of {@code original}'s own
     * indices, or {@code null} when it does not occur. Used to highlight a match in the text the
     * user actually sees, where an accent or a ligature makes the two lengths differ.
     */
    public static int[] locate(String original, String query) {
        if (original == null || original.isEmpty() || query == null || query.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder(original.length());
        IndexMap map = new IndexMap(original.length());
        boolean pendingSpace = false;
        for (int i = 0; i < original.length(); ) {
            int cp = original.codePointAt(i);
            int next = i + Character.charCount(cp);
            if (cp < 0x80) {
                pendingSpace = append(out, cp, pendingSpace, map, i, next);
            } else {
                String piece =
                        Normalizer.normalize(original.substring(i, next), Normalizer.Form.NFKD);
                for (int j = 0; j < piece.length(); ) {
                    int pcp = piece.codePointAt(j);
                    j += Character.charCount(pcp);
                    pendingSpace = append(out, pcp, pendingSpace, map, i, next);
                }
            }
            i = next;
        }
        int at = out.indexOf(query);
        if (at < 0) return null;
        return new int[] {map.start[at], map.end[at + query.length() - 1]};
    }

    /** Whether the character at a folded index is a letter or digit (a word, not a boundary). */
    static boolean isWordChar(String folded, int index) {
        if (index < 0 || index >= folded.length()) return false;
        return Character.isLetterOrDigit(folded.codePointAt(index));
    }

    /**
     * Appends one decomposed code point to {@code out} and returns whether a space is still
     * pending. The space is written only when a visible character follows it, which collapses runs
     * and trims both ends in a single pass.
     */
    private static boolean append(
            StringBuilder out, int cp, boolean pendingSpace, IndexMap map, int from, int to) {
        if (isMark(cp)) return pendingSpace;
        if (isSpace(cp)) return pendingSpace || out.length() > 0;
        if (pendingSpace) {
            out.append(' ');
            if (map != null) map.add(from, to);
        }
        int folded = fold(cp);
        out.appendCodePoint(folded);
        if (map != null) {
            for (int k = Character.charCount(folded); k > 0; k--) map.add(from, to);
        }
        return false;
    }

    private static int fold(int cp) {
        int lower = Character.toLowerCase(cp);
        // Final sigma is only a spelling of sigma; fold it so a word ending in it still matches.
        return lower == '\u03C2' ? '\u03C3' : lower;
    }

    private static boolean isMark(int cp) {
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static boolean isSpace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 0x80) return false;
        }
        return true;
    }

    /** Original {@code [start, end)} range behind each folded char. */
    private static final class IndexMap {
        int[] start;
        int[] end;
        int size;

        IndexMap(int capacity) {
            start = new int[Math.max(capacity, 4)];
            end = new int[start.length];
        }

        void add(int from, int to) {
            if (size == start.length) {
                start = Arrays.copyOf(start, size * 2);
                end = Arrays.copyOf(end, size * 2);
            }
            start[size] = from;
            end[size] = to;
            size++;
        }
    }
}
