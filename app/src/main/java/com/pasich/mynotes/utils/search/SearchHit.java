package com.pasich.mynotes.utils.search;

import com.pasich.mynotes.data.model.Note;
import java.util.Objects;

/** One search result: the note, why it matched, and the folded query that found it. */
public final class SearchHit {

    private final Note note;
    private final MatchKind kind;
    private final String query;

    public SearchHit(Note note, MatchKind kind, String query) {
        this.note = note;
        this.kind = kind;
        this.query = query;
    }

    public Note note() {
        return note;
    }

    public MatchKind kind() {
        return kind;
    }

    /** The query as {@link SearchText#normalize} folded it, ready for {@link SearchText#locate}. */
    public String query() {
        return query;
    }

    /** Whether this hit draws the same card as {@code other}: same note row, same highlight. */
    public boolean sameContentAs(SearchHit other) {
        return note == other.note && kind == other.kind && query.equals(other.query);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchHit)) return false;
        return sameContentAs((SearchHit) o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(System.identityHashCode(note), kind, query);
    }

    /** Ranking buckets, best first. */
    public enum MatchKind {
        /** The whole title is the query. */
        EXACT_TITLE,
        /** The title starts with the query. */
        TITLE_PREFIX,
        /** The query is a whole word (or words) inside the title. */
        TITLE_WORD,
        /** The query appears anywhere in the title. */
        TITLE_CONTAINS,
        /** The note's tag name contains the query. */
        TAG,
        /** Only the note's text contains the query. */
        BODY
    }
}
