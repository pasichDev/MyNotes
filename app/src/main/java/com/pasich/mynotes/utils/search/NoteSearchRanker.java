package com.pasich.mynotes.utils.search;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.utils.search.SearchHit.MatchKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Ranks notes against a search query.
 *
 * <p>Matching is case-insensitive and ignores diacritics and extra whitespace (see {@link
 * SearchText}). Results come in buckets, best first: exact title, title starts with the query, the
 * query as a whole word in the title, the query anywhere in the title, the tag name, and finally
 * the note text. Inside a bucket pinned notes come first, then the newest.
 *
 * <p>A one-character query only looks at titles: a single letter is in almost every note's text, so
 * those matches would bury the useful ones. The text is searched from {@link
 * #MIN_BODY_QUERY_LENGTH} characters.
 *
 * <p>Each note is folded once per list, not once per keystroke: the folded forms are kept for as
 * long as the same list instance keeps coming back, which is the case while only the query changes.
 * Not thread-safe; the presenter calls it from one stream.
 */
public final class NoteSearchRanker {

    /** Shortest query, in folded characters, that also searches tag names and note text. */
    public static final int MIN_BODY_QUERY_LENGTH = 2;

    private static final Comparator<Ranked> ORDER =
            Comparator.<Ranked>comparingInt(r -> r.kind.ordinal())
                    .thenComparing(r -> !r.note.isPinned())
                    .thenComparing(r -> r.note.getDate(), Comparator.reverseOrder())
                    .thenComparing(r -> r.note.getId(), Comparator.reverseOrder());

    private List<Note> indexedNotes;
    private Folded[] index;

    /**
     * Whether {@code rawQuery} is too short to search note text, so an empty result should ask the
     * user to keep typing rather than say nothing was found.
     */
    public static boolean searchesTitlesOnly(String rawQuery) {
        return SearchText.normalize(rawQuery).length() < MIN_BODY_QUERY_LENGTH;
    }

    /**
     * Returns the notes matching {@code rawQuery}, best first. An empty query matches nothing. A
     * non-empty {@code tagFilter} keeps only notes with exactly that tag.
     */
    public List<SearchHit> rank(List<Note> notes, String rawQuery, String tagFilter) {
        String query = SearchText.normalize(rawQuery);
        if (query.isEmpty() || notes == null || notes.isEmpty()) return Collections.emptyList();

        boolean titlesOnly = query.length() < MIN_BODY_QUERY_LENGTH;
        boolean hasTagFilter = tagFilter != null && !tagFilter.isEmpty();
        Folded[] folded = indexFor(notes);

        List<Ranked> ranked = new ArrayList<>();
        for (int i = 0; i < folded.length; i++) {
            Note note = notes.get(i);
            if (hasTagFilter && !tagFilter.equals(note.getTag())) continue;
            MatchKind kind = classify(folded[i], query, titlesOnly);
            if (kind != null) ranked.add(new Ranked(note, kind));
        }
        ranked.sort(ORDER);

        List<SearchHit> hits = new ArrayList<>(ranked.size());
        for (Ranked r : ranked) hits.add(new SearchHit(r.note, r.kind, query));
        return hits;
    }

    private Folded[] indexFor(List<Note> notes) {
        if (notes != indexedNotes || index == null || index.length != notes.size()) {
            Folded[] fresh = new Folded[notes.size()];
            for (int i = 0; i < fresh.length; i++) fresh[i] = new Folded(notes.get(i));
            index = fresh;
            indexedNotes = notes;
        }
        return index;
    }

    private static MatchKind classify(Folded note, String query, boolean titlesOnly) {
        String title = note.title;
        int at = title.indexOf(query);
        if (at >= 0) {
            if (at == 0 && title.length() == query.length()) return MatchKind.EXACT_TITLE;
            if (at == 0) return MatchKind.TITLE_PREFIX;
            if (hasWholeWord(title, query, at)) return MatchKind.TITLE_WORD;
            return MatchKind.TITLE_CONTAINS;
        }
        if (titlesOnly) return null;
        if (note.tag.contains(query)) return MatchKind.TAG;
        if (note.body.contains(query)) return MatchKind.BODY;
        return null;
    }

    /** Whether any occurrence of {@code query} from {@code from} on stands as whole words. */
    private static boolean hasWholeWord(String text, String query, int from) {
        for (int at = from; at >= 0; at = text.indexOf(query, at + 1)) {
            int end = at + query.length();
            boolean startsWord =
                    !SearchText.isWordChar(text, at - 1) || !SearchText.isWordChar(text, at);
            boolean endsWord =
                    !SearchText.isWordChar(text, end) || !SearchText.isWordChar(text, end - 1);
            if (startsWord && endsWord) return true;
        }
        return false;
    }

    private static final class Folded {
        final String title;
        final String tag;
        final String body;

        Folded(Note note) {
            title = SearchText.normalize(note.getTitle());
            tag = SearchText.normalize(note.getTag());
            body = SearchText.normalize(note.getValue());
        }
    }

    private static final class Ranked {
        final Note note;
        final MatchKind kind;

        Ranked(Note note, MatchKind kind) {
            this.note = note;
            this.kind = kind;
        }
    }
}
