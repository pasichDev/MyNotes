package com.pasich.mynotes.utils.search;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class SearchHintFitterTest {

    @Test
    public void fullHintThatFits_isKept() {
        assertThat(SearchHintFitter.choose(180f, 200f, "In Notizen suchen", "Suchen"))
                .isEqualTo("In Notizen suchen");
    }

    @Test
    public void fullHintTooWide_fallsBackToShortHint() {
        assertThat(SearchHintFitter.choose(320f, 200f, "In Notizen suchen", "Suchen"))
                .isEqualTo("Suchen");
    }

    @Test
    public void subPixelRounding_doesNotDropTheFullHint() {
        assertThat(SearchHintFitter.choose(200.3f, 200f, "Search notes", "Search"))
                .isEqualTo("Search notes");
    }
}
