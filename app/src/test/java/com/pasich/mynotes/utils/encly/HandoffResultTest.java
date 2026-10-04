package com.pasich.mynotes.utils.encly;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class HandoffResultTest {

    private static final int RESULT_OK = -1; // android.app.Activity.RESULT_OK
    private static final int RESULT_CANCELED = 0; // android.app.Activity.RESULT_CANCELED

    @Test
    public void ok_withCounts() {
        HandoffResult result = HandoffResult.parse(RESULT_OK, 12, 4, 3, 1, null);

        assertThat(result.success).isTrue();
        assertThat(result.notes).isEqualTo(12);
        assertThat(result.tasks).isEqualTo(4);
        assertThat(result.tags).isEqualTo(3);
        assertThat(result.skipped).isEqualTo(1);
        assertThat(result.reason).isNull();
    }

    @Test
    public void ok_withMissingOrNegativeCounts_readsZero() {
        HandoffResult result = HandoffResult.parse(RESULT_OK, null, null, -5, null, null);

        assertThat(result.success).isTrue();
        assertThat(result.notes).isEqualTo(0);
        assertThat(result.tasks).isEqualTo(0);
        assertThat(result.tags).isEqualTo(0);
        assertThat(result.skipped).isEqualTo(0);
    }

    @Test
    public void canceled_withEveryKnownReason() {
        assertThat(reason("untrusted_caller")).isEqualTo(HandoffResult.Reason.UNTRUSTED_CALLER);
        assertThat(reason("cancelled")).isEqualTo(HandoffResult.Reason.CANCELLED);
        assertThat(reason("unsupported_schema")).isEqualTo(HandoffResult.Reason.UNSUPPORTED_SCHEMA);
        assertThat(reason("invalid_payload")).isEqualTo(HandoffResult.Reason.INVALID_PAYLOAD);
        assertThat(reason("too_large")).isEqualTo(HandoffResult.Reason.TOO_LARGE);
        assertThat(reason("failed")).isEqualTo(HandoffResult.Reason.FAILED);
    }

    @Test
    public void canceled_withoutReason_isACancellation() {
        // Encly crashed, or the user backed out before it answered.
        HandoffResult result = HandoffResult.parse(RESULT_CANCELED, null, null, null, null, null);

        assertThat(result.success).isFalse();
        assertThat(result.reason).isEqualTo(HandoffResult.Reason.CANCELLED);
        assertThat(reason("")).isEqualTo(HandoffResult.Reason.CANCELLED);
    }

    @Test
    public void canceled_ignoresCountsItWasGiven() {
        HandoffResult result = HandoffResult.parse(RESULT_CANCELED, 9, 9, 9, 9, "failed");

        assertThat(result.success).isFalse();
        assertThat(result.notes).isEqualTo(0);
    }

    @Test
    public void unknownReason_isAFailure_notASilentCancel() {
        assertThat(reason("disk_full")).isEqualTo(HandoffResult.Reason.FAILED);
    }

    @Test
    public void anyOtherResultCode_isNotASuccess() {
        HandoffResult result = HandoffResult.parse(1, 5, 5, 5, 5, null);

        assertThat(result.success).isFalse();
        assertThat(result.reason).isEqualTo(HandoffResult.Reason.CANCELLED);
    }

    private static HandoffResult.Reason reason(String wire) {
        return HandoffResult.parse(RESULT_CANCELED, null, null, null, null, wire).reason;
    }
}
