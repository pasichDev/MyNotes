package com.pasich.mynotes.utils.search;

import static com.google.common.truth.Truth.assertThat;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.utils.search.SearchHit.MatchKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Tests the production {@link NoteSearchRanker} and {@link SearchText} used by main search. */
public class SearchFilterTest {

    private final NoteSearchRanker ranker = new NoteSearchRanker();
    private int nextId = 1;

    private Note note(String title, String body, long date) {
        return note(title, body, date, "");
    }

    private Note note(String title, String body, long date, String tag) {
        Note n = new Note().create(title, body, date, tag);
        n.setId(nextId++);
        return n;
    }

    private List<String> titles(List<Note> notes, String query) {
        return titles(notes, query, "");
    }

    private List<String> titles(List<Note> notes, String query, String tagFilter) {
        List<String> out = new ArrayList<>();
        for (SearchHit hit : ranker.rank(notes, query, tagFilter)) out.add(hit.note().getTitle());
        return out;
    }

    // ---- Ranking buckets (#175) ---------------------------------------------------------------

    @Test
    public void exactTitle_outranksNewerBodyMatches() {
        List<Note> notes =
                Arrays.asList(
                        note("Weekly", "review the PR before lunch", 300),
                        note("Standup", "two PRs left", 200),
                        note("PR", "", 100));
        assertThat(titles(notes, "PR")).containsExactly("PR", "Weekly", "Standup").inOrder();
    }

    @Test
    public void allBuckets_inIssueOrder() {
        List<Note> notes =
                Arrays.asList(
                        note("Notes", "a plan for the week", 600),
                        note("Groceries", "", 500, "plan"),
                        note("Airplane", "", 400),
                        note("My plan for May", "", 300),
                        note("Planning", "", 200),
                        note("Plan", "", 100));
        List<SearchHit> hits = ranker.rank(notes, "plan", "");

        List<MatchKind> kinds = new ArrayList<>();
        for (SearchHit h : hits) kinds.add(h.kind());
        assertThat(kinds)
                .containsExactly(
                        MatchKind.EXACT_TITLE,
                        MatchKind.TITLE_PREFIX,
                        MatchKind.TITLE_WORD,
                        MatchKind.TITLE_CONTAINS,
                        MatchKind.TAG,
                        MatchKind.BODY)
                .inOrder();
        assertThat(titles(notes, "plan"))
                .containsExactly(
                        "Plan", "Planning", "My plan for May", "Airplane", "Groceries", "Notes")
                .inOrder();
    }

    @Test
    public void wholeWord_needsBoundariesOnBothSides() {
        List<Note> notes =
                Arrays.asList(note("Code review", "", 300), note("Ask a PR-review", "", 200));
        List<SearchHit> hits = ranker.rank(notes, "review", "");
        assertThat(hits.get(0).kind()).isEqualTo(MatchKind.TITLE_WORD);
        assertThat(hits.get(1).kind()).isEqualTo(MatchKind.TITLE_WORD);

        List<SearchHit> partial =
                ranker.rank(Collections.singletonList(note("Reviews", "", 1)), "view", "");
        assertThat(partial.get(0).kind()).isEqualTo(MatchKind.TITLE_CONTAINS);
    }

    @Test
    public void laterWholeWordOccurrence_stillCounts() {
        // The first "art" is inside "Start", the second stands alone.
        List<SearchHit> hits =
                ranker.rank(Collections.singletonList(note("Start art club", "", 1)), "art", "");
        assertThat(hits.get(0).kind()).isEqualTo(MatchKind.TITLE_WORD);
    }

    // ---- Normalization ------------------------------------------------------------------------

    @Test
    public void caseAndDiacritics_areIgnored() {
        List<Note> notes =
                Arrays.asList(
                        note("Café menu", "", 300),
                        note("CAFE", "", 200),
                        note("Other", "naïve plan", 100));
        assertThat(titles(notes, "cafe")).containsExactly("CAFE", "Café menu").inOrder();
        assertThat(titles(notes, "CAFÉ")).containsExactly("CAFE", "Café menu").inOrder();
        assertThat(titles(notes, "naive")).containsExactly("Other");
    }

    @Test
    public void cyrillic_matchesCaseInsensitively() {
        List<Note> notes = Arrays.asList(note("Список", "", 200), note("Інше", "список", 100));
        // "СПИСОК" finds the title exactly and the other note through its text.
        assertThat(titles(notes, "СПИСОК")).containsExactly("Список", "Інше").inOrder();
    }

    @Test
    public void trailingAndInnerSpaces_areCollapsed() {
        List<Note> notes =
                Arrays.asList(note("Shopping   list", "", 200), note("Shopping", "", 100));
        List<SearchHit> hits = ranker.rank(notes, "  shopping list  ", "");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).kind()).isEqualTo(MatchKind.EXACT_TITLE);

        // A trailing space used to defeat the exact-title check.
        assertThat(ranker.rank(notes, "shopping ", "").get(0).kind())
                .isEqualTo(MatchKind.EXACT_TITLE);
    }

    @Test
    public void normalize_foldsCompatibilityForms() {
        assertThat(SearchText.normalize("  ﬁle \tNOTES ")).isEqualTo("file notes");
        assertThat(SearchText.normalize("Ångström")).isEqualTo("angstrom");
        assertThat(SearchText.normalize(null)).isEmpty();
    }

    @Test
    public void locate_returnsRangeInOriginalText() {
        // "é" here is e + a combining accent: two chars that fold to one.
        String title = "Le café  crème";
        int[] range = SearchText.locate(title, SearchText.normalize("café creme"));
        assertThat(range).isNotNull();
        assertThat(title.substring(range[0], range[1])).isEqualTo("café  crème");

        assertThat(SearchText.locate("Groceries", "plan")).isNull();
    }

    // ---- Short queries ------------------------------------------------------------------------

    @Test
    public void emptyOrBlankQuery_matchesNothing() {
        List<Note> notes = Arrays.asList(note("A", "a", 1), note("B", "b", 2));
        assertThat(ranker.rank(notes, "", "")).isEmpty();
        assertThat(ranker.rank(notes, "   ", "")).isEmpty();
        assertThat(ranker.rank(notes, null, "")).isEmpty();
        assertThat(NoteSearchRanker.searchesTitlesOnly("  ")).isTrue();
    }

    @Test
    public void oneCharacter_searchesTitlesOnly() {
        List<Note> notes =
                Arrays.asList(
                        note("Notes", "x marks the spot", 300, "x"),
                        note("Box", "", 200),
                        note("X", "", 100));
        assertThat(titles(notes, "x")).containsExactly("X", "Box").inOrder();
        assertThat(NoteSearchRanker.searchesTitlesOnly(" x ")).isTrue();
        assertThat(NoteSearchRanker.searchesTitlesOnly("xm")).isFalse();
    }

    @Test
    public void twoCharacters_alsoSearchTagsAndText() {
        List<Note> notes =
                Arrays.asList(note("Notes", "x marks the spot", 300), note("Box", "", 200));
        assertThat(titles(notes, "x ma")).containsExactly("Notes");
        assertThat(titles(notes, "ox")).containsExactly("Box");
    }

    // ---- Ties and filters ---------------------------------------------------------------------

    @Test
    public void sameBucket_pinnedFirstThenNewest() {
        Note oldPinned = note("Trip budget", "", 100);
        oldPinned.setPinned(true);
        List<Note> notes =
                Arrays.asList(note("Trip plan", "", 200), oldPinned, note("Trip photos", "", 300));
        assertThat(titles(notes, "trip"))
                .containsExactly("Trip budget", "Trip photos", "Trip plan")
                .inOrder();
    }

    @Test
    public void pinning_neverBeatsABetterBucket() {
        Note pinnedBody = note("Ideas", "PR checklist", 999);
        pinnedBody.setPinned(true);
        List<Note> notes = Arrays.asList(pinnedBody, note("PR", "", 1));
        assertThat(titles(notes, "pr")).containsExactly("PR", "Ideas").inOrder();
    }

    @Test
    public void tagFilter_keepsOnlyThatTag() {
        List<Note> notes =
                Arrays.asList(
                        note("Work plan", "", 200, "work"), note("Home plan", "", 100, "home"));
        assertThat(titles(notes, "plan", "home")).containsExactly("Home plan");
        assertThat(titles(notes, "plan", null)).containsExactly("Work plan", "Home plan").inOrder();
    }

    @Test
    public void noMatch_returnsEmpty() {
        List<Note> notes = Arrays.asList(note("Alpha", "beta", 1), note("Gamma", "delta", 2));
        assertThat(ranker.rank(notes, "zzznomatch", "")).isEmpty();
    }

    @Test
    public void hitsCarryTheFoldedQuery() {
        List<SearchHit> hits =
                ranker.rank(Collections.singletonList(note("Café", "", 1)), " CAFÉ ", "");
        assertThat(hits.get(0).query()).isEqualTo("cafe");
    }

    @Test
    public void newListInstance_isReindexed() {
        Note before = note("Draft", "", 1);
        assertThat(titles(Collections.singletonList(before), "draft")).hasSize(1);

        Note renamed = note("Final", "", 1);
        renamed.setId(before.getId());
        List<Note> afterSync = new ArrayList<>(Collections.singletonList(renamed));
        assertThat(titles(afterSync, "draft")).isEmpty();
        assertThat(titles(afterSync, "final")).containsExactly("Final");
    }

    // ---- Speed --------------------------------------------------------------------------------

    @Test
    public void fiveThousandNotes_rankQuickly() {
        List<Note> notes = new ArrayList<>();
        String body = "Зустріч з командою, " + "café notes and a long paragraph of ordinary text. ";
        StringBuilder longBody = new StringBuilder();
        for (int i = 0; i < 20; i++) longBody.append(body);
        for (int i = 0; i < 5000; i++) {
            notes.add(note("Note " + i + " meeting", longBody.toString(), i));
        }
        notes.add(note("PR", "", 0));

        long start = System.nanoTime();
        List<SearchHit> first = ranker.rank(notes, "pr", "");
        for (String q : new String[] {"p", "pr", "mee", "meeting", "café", "zz"}) {
            ranker.rank(notes, q, "");
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(first.get(0).note().getTitle()).isEqualTo("PR");
        // A smoke bound, generous enough for a loaded CI machine: indexing 5k notes once plus
        // seven queries over them.
        assertThat(elapsedMs).isLessThan(3000L);
    }
}
