package com.pasich.mynotes.data.sync;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class SyncMetadataTest {

    @Test
    public void newStableId_isCanonicalLowercaseUuid() {
        String stableId = SyncMetadata.newStableId();

        assertThat(stableId)
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    public void nextUpdatedAt_advancesWhenClockMovesBackwards() {
        assertThat(SyncMetadata.nextUpdatedAt(100L, 101L)).isEqualTo(101L);
        assertThat(SyncMetadata.nextUpdatedAt(100L, 100L)).isEqualTo(101L);
        assertThat(SyncMetadata.nextUpdatedAt(100L, 99L)).isEqualTo(101L);
    }

    @Test
    public void supportedRecordTypes_matchSyncSchema() {
        assertThat(SyncMetadata.isSupportedRecordType(SyncMetadata.RECORD_TYPE_NOTE)).isTrue();
        assertThat(SyncMetadata.isSupportedRecordType(SyncMetadata.RECORD_TYPE_TASK)).isTrue();
        assertThat(SyncMetadata.isSupportedRecordType(SyncMetadata.RECORD_TYPE_TAG)).isTrue();
        assertThat(SyncMetadata.isSupportedRecordType("task_category")).isFalse();
    }
}
