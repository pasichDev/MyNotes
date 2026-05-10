package com.pasich.mynotes.utils;

import static com.google.common.truth.Truth.assertThat;

import com.pasich.mynotes.data.model.Note;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class SearchFilterTest {

    private Note makeNote(String title, String value) {
        return new Note().create(title, value, new Date().getTime(), "");
    }

    private List<Note> filter(List<Note> notes, String query) {
        if (query == null || query.trim().isEmpty()) return notes;
        String q = query.toLowerCase().trim();
        return notes.stream()
                .filter(n -> n.getTitle().toLowerCase().contains(q)
                        || n.getValue().toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    @Test
    public void emptyQuery_returnsAllNotes() {
        List<Note> notes = Arrays.asList(makeNote("A", "a"), makeNote("B", "b"));
        assertThat(filter(notes, "")).hasSize(2);
    }

    @Test
    public void exactTitleMatch_returnsNote() {
        List<Note> notes = Arrays.asList(makeNote("Shopping List", "milk"), makeNote("Work", "tasks"));
        assertThat(filter(notes, "Shopping List")).hasSize(1);
    }

    @Test
    public void partialTitleMatch_returnsNote() {
        List<Note> notes = Arrays.asList(makeNote("My Shopping", "items"), makeNote("Work", "tasks"));
        assertThat(filter(notes, "Shop")).hasSize(1);
    }

    @Test
    public void caseInsensitiveSearch_works() {
        List<Note> notes = Arrays.asList(makeNote("UPPERCASE", "content"), makeNote("Other", "stuff"));
        assertThat(filter(notes, "uppercase")).hasSize(1);
    }

    @Test
    public void contentSearch_matchesValue() {
        List<Note> notes = Arrays.asList(makeNote("Title", "buy groceries"), makeNote("Title2", "meeting notes"));
        assertThat(filter(notes, "groceries")).hasSize(1);
    }

    @Test
    public void noMatch_returnsEmpty() {
        List<Note> notes = Arrays.asList(makeNote("Alpha", "beta"), makeNote("Gamma", "delta"));
        assertThat(filter(notes, "zzznomatch")).isEmpty();
    }

    @Test
    public void nullQuery_returnsAllNotes() {
        List<Note> notes = Arrays.asList(makeNote("A", "a"), makeNote("B", "b"));
        assertThat(filter(notes, null)).hasSize(2);
    }
}
