package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import java.util.Collections;
import org.junit.Test;

public class SnapshotProblemTest {

    private static final String NOTE_ID = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    public void theFailureNamesTheNoteItIsIn() throws java.io.IOException {
        // "MISSING_ATTACHMENT" alone left the user to guess which of their notes to open; the
        // account screen shows this string as the sync status.
        SnapshotBuildResult result =
                SnapshotBuildResult.incomplete(
                        SyncSnapshot.empty(),
                        Collections.singletonList(
                                new SnapshotProblem(
                                        SnapshotProblem.Kind.MISSING_ATTACHMENT,
                                        SyncMetadata.RECORD_TYPE_NOTE,
                                        NOTE_ID,
                                        "Shopping list")));

        try {
            result.requireSnapshot();
            throw new AssertionError("Expected an incomplete snapshot to be refused");
        } catch (SnapshotBuildResult.SnapshotBuildException expected) {
            assertThat(expected)
                    .hasMessageThat()
                    .isEqualTo(
                            "Local snapshot is incomplete: MISSING_ATTACHMENT in note"
                                    + " \"Shopping list\"");
        }
    }

    @Test
    public void aLongTitleIsCutShortAndABlankOneIsDropped() {
        String title = "x".repeat(80);

        SnapshotProblem long_ =
                new SnapshotProblem(
                        SnapshotProblem.Kind.MISSING_ATTACHMENT,
                        SyncMetadata.RECORD_TYPE_NOTE,
                        NOTE_ID,
                        title);
        SnapshotProblem blank =
                new SnapshotProblem(
                        SnapshotProblem.Kind.MISSING_ATTACHMENT,
                        SyncMetadata.RECORD_TYPE_NOTE,
                        NOTE_ID,
                        "   ");

        assertThat(long_.getLabel()).hasLength(41);
        assertThat(blank.getLabel()).isNull();
        assertThat(blank.describe()).isEqualTo("MISSING_ATTACHMENT");
    }
}
