package com.pasich.mynotes.ui.sync;

import static com.google.common.truth.Truth.assertThat;

import com.pasich.mynotes.data.database.entities.SyncConflictEntity;
import com.pasich.mynotes.data.sync.SyncMetadata;
import org.junit.Test;

/**
 * What the conflict dialog shows for a stored conflict.
 *
 * <p>The old dialog rendered both versions into one string with no timestamps and cut the text at a
 * fixed 120 characters from the start, so a difference near the end of a note never reached the
 * screen. These are the rules that replace it.
 */
public class SyncConflictPresentationTest {

    private static final String NOTE = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    public void namesEachSideByItsOwnOrigin() {
        SyncConflictPresentation presentation =
                SyncConflictPresentation.of(
                        conflict(
                                "note",
                                "LOCAL",
                                "REMOTE",
                                note("Список", "Молоко"),
                                note("Список", "Хліб")));

        assertThat(presentation.winner.local).isTrue();
        assertThat(presentation.alternative.local).isFalse();
    }

    @Test
    public void aDriveVersusDriveConflictClaimsNeitherSideIsLocal() {
        SyncConflictPresentation presentation =
                SyncConflictPresentation.of(
                        conflict("note", "REMOTE", "REMOTE", note("A", "one"), note("A", "two")));

        // Naming an arbitrary remote version "this device" is what the version-addressed
        // resolution model exists to stop; the dialog must not reintroduce it.
        assertThat(presentation.winner.local).isFalse();
        assertThat(presentation.alternative.local).isFalse();
    }

    @Test
    public void marksTheNewerSideWhicheverItIs() {
        SyncConflictEntity newerWinner =
                conflict("note", "LOCAL", "REMOTE", note("A", "x"), note("A", "y"));
        newerWinner.winnerUpdatedAt = 200L;
        newerWinner.loserUpdatedAt = 100L;

        SyncConflictPresentation presentation = SyncConflictPresentation.of(newerWinner);

        assertThat(presentation.winner.newer).isTrue();
        assertThat(presentation.alternative.newer).isFalse();
        assertThat(presentation.winner.updatedAt).isEqualTo(200L);
        assertThat(presentation.alternative.updatedAt).isEqualTo(100L);
    }

    @Test
    public void marksTheAlternativeNewerWhenItIs() {
        SyncConflictEntity newerAlternative =
                conflict("note", "LOCAL", "REMOTE", note("A", "x"), note("A", "y"));
        newerAlternative.winnerUpdatedAt = 100L;
        newerAlternative.loserUpdatedAt = 300L;

        SyncConflictPresentation presentation = SyncConflictPresentation.of(newerAlternative);

        assertThat(presentation.winner.newer).isFalse();
        assertThat(presentation.alternative.newer).isTrue();
    }

    @Test
    public void readsNoteTitleAndBodyThroughTheirSerializedAliases() {
        SyncConflictPresentation presentation =
                SyncConflictPresentation.of(
                        conflict(
                                "note",
                                "LOCAL",
                                "REMOTE",
                                note("Список покупок", "Молоко"),
                                note("Список покупок", "Хліб")));

        // Note serializes title as "b" and value as "c"; probing "title"/"value" matched nothing
        // and every note conflict showed the same placeholder on both sides.
        assertThat(presentation.winner.kind).isEqualTo(SyncConflictPresentation.Kind.TEXT);
        assertThat(presentation.winner.preview).contains("Список покупок");
        assertThat(presentation.winner.preview).contains("Молоко");
        assertThat(presentation.alternative.preview).contains("Хліб");
    }

    @Test
    public void highlightsOnlyThePartThatDiffers() {
        SyncConflictPresentation presentation =
                SyncConflictPresentation.of(
                        conflict(
                                "note",
                                "LOCAL",
                                "REMOTE",
                                note("Покупки", "Молоко, хліб, кава"),
                                note("Покупки", "Молоко, хліб, сир")));

        String winner = presentation.winner.preview;
        assertThat(presentation.winner.hasHighlight()).isTrue();
        assertThat(
                        winner.substring(
                                presentation.winner.highlightStart,
                                presentation.winner.highlightEnd))
                .isEqualTo("кава");
        String alternative = presentation.alternative.preview;
        assertThat(
                        alternative.substring(
                                presentation.alternative.highlightStart,
                                presentation.alternative.highlightEnd))
                .isEqualTo("сир");
    }

    @Test
    public void keepsADifferenceVisibleEvenWhenItIsPastThePreviewLimit() {
        String shared = repeat("одне й те саме ", 30);
        SyncConflictPresentation presentation =
                SyncConflictPresentation.of(
                        conflict(
                                "note",
                                "LOCAL",
                                "REMOTE",
                                note("Довга", shared + "КАВА"),
                                note("Довга", shared + "СИР")));

        // Cutting the head of the string is exactly how the old dialog hid this.
        assertThat(presentation.winner.preview.length())
                .isAtMost(SyncConflictPresentation.PREVIEW_LIMIT + 2);
        assertThat(presentation.winner.preview).contains("КАВА");
        assertThat(
                        presentation.winner.preview.substring(
                                presentation.winner.highlightStart,
                                presentation.winner.highlightEnd))
                .isEqualTo("КАВА");
    }

    @Test
    public void reportsADeletedVersionAsADeletion() {
        SyncConflictEntity conflict =
                conflict("note", "LOCAL", "REMOTE", note("A", "body"), tombstone());
        conflict.loserTombstone = true;

        SyncConflictPresentation presentation = SyncConflictPresentation.of(conflict);

        assertThat(presentation.alternative.kind).isEqualTo(SyncConflictPresentation.Kind.DELETED);
        assertThat(presentation.winner.kind).isEqualTo(SyncConflictPresentation.Kind.TEXT);
    }

    @Test
    public void reportsAPreferencesConflictAsSettings() {
        SyncConflictPresentation presentation =
                SyncConflictPresentation.of(
                        conflict(
                                SyncMetadata.RECORD_TYPE_PREFERENCES,
                                "LOCAL",
                                "REMOTE",
                                "{\"payload\":{\"c\":1}}",
                                "{\"payload\":{\"c\":2}}"));

        assertThat(presentation.winner.kind).isEqualTo(SyncConflictPresentation.Kind.SETTINGS);
        assertThat(presentation.alternative.kind).isEqualTo(SyncConflictPresentation.Kind.SETTINGS);
    }

    @Test
    public void reportsAReadableButEmptyVersionAsUntitled() {
        SyncConflictPresentation presentation =
                SyncConflictPresentation.of(
                        conflict("note", "LOCAL", "REMOTE", note("", ""), note("", "")));

        assertThat(presentation.winner.kind).isEqualTo(SyncConflictPresentation.Kind.UNTITLED);
    }

    @Test
    public void survivesUnreadableStoredJson() {
        SyncConflictPresentation presentation =
                SyncConflictPresentation.of(
                        conflict("note", "LOCAL", "REMOTE", "{not json", note("A", "b")));

        // A corrupt row must still render something rather than take the dialog down.
        assertThat(presentation.winner.kind).isEqualTo(SyncConflictPresentation.Kind.UNTITLED);
        assertThat(presentation.alternative.kind).isEqualTo(SyncConflictPresentation.Kind.TEXT);
    }

    @Test
    public void identicalTextProducesNoHighlight() {
        SyncConflictPresentation presentation =
                SyncConflictPresentation.of(
                        conflict("note", "LOCAL", "REMOTE", note("A", "same"), note("A", "same")));

        assertThat(presentation.winner.hasHighlight()).isFalse();
        assertThat(presentation.alternative.hasHighlight()).isFalse();
    }

    @Test
    public void differenceRangeFindsTheChangedMiddle() {
        int[] range = SyncConflictPresentation.differenceRange("abcXYZdef", "abcQdef");

        assertThat(range[0]).isEqualTo(3);
        assertThat("abcXYZdef".substring(range[0], range[1])).isEqualTo("XYZ");
        assertThat("abcQdef".substring(range[0], range[2])).isEqualTo("Q");
    }

    @Test
    public void differenceRangeHandlesAPureAppend() {
        int[] range = SyncConflictPresentation.differenceRange("abc", "abcdef");

        assertThat("abc".substring(range[0], range[1])).isEmpty();
        assertThat("abcdef".substring(range[0], range[2])).isEqualTo("def");
    }

    @Test
    public void differenceRangeHandlesAnEmptySide() {
        int[] range = SyncConflictPresentation.differenceRange("", "abc");

        assertThat(range[0]).isEqualTo(0);
        assertThat("abc".substring(range[0], range[2])).isEqualTo("abc");
    }

    private static String repeat(String value, int times) {
        StringBuilder result = new StringBuilder(value.length() * times);
        for (int i = 0; i < times; i++) result.append(value);
        return result.toString();
    }

    private static String note(String title, String body) {
        return "{\"type\":\"note\",\"id\":\""
                + NOTE
                + "\",\"updatedAt\":\"2026-08-31T12:00:00Z\",\"deletedAt\":null,"
                + "\"payload\":{\"b\":\""
                + title
                + "\",\"c\":\""
                + body
                + "\"}}";
    }

    private static String tombstone() {
        return "{\"type\":\"note\",\"id\":\""
                + NOTE
                + "\",\"updatedAt\":\"2026-08-31T12:00:00Z\","
                + "\"deletedAt\":\"2026-08-31T12:00:05Z\"}";
    }

    private static SyncConflictEntity conflict(
            String recordType,
            String winnerSource,
            String loserSource,
            String winnerJson,
            String loserJson) {
        return new SyncConflictEntity(
                recordType,
                NOTE,
                "pair",
                winnerSource,
                loserSource,
                "winner-version",
                "loser-version",
                winnerJson,
                loserJson,
                200L,
                100L,
                false,
                false,
                "PENDING",
                false,
                1L,
                0L);
    }
}
