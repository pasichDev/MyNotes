package com.pasich.mynotes.utils.reminder;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import com.pasich.mynotes.data.model.RepeatRule;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ReminderDraftTest {

    @Test
    public void everythingChosenSurvivesTheSavedState() {
        ReminderDraft draft = new ReminderDraft();
        draft.time = 1_800_000_000_000L;
        draft.repeatChipId = 42;
        draft.customRule = RepeatRule.every(3, RepeatRule.Unit.HOURS, null);
        draft.intervalMinutes = 15;
        draft.pickedDateUtc = 1_799_971_200_000L;

        Bundle state = new Bundle();
        draft.save(state);
        ReminderDraft restored = ReminderDraft.restore(state);

        assertThat(restored).isNotNull();
        assertThat(restored.time).isEqualTo(1_800_000_000_000L);
        assertThat(restored.repeatChipId).isEqualTo(42);
        assertThat(restored.customRule.getInterval()).isEqualTo(3);
        assertThat(restored.customRule.getUnit()).isEqualTo(RepeatRule.Unit.HOURS);
        assertThat(restored.intervalMinutes).isEqualTo(15);
        assertThat(restored.pickedDateUtc).isEqualTo(1_799_971_200_000L);
    }

    @Test
    public void nothingChosenYet_restoresEmpty() {
        Bundle state = new Bundle();
        new ReminderDraft().save(state);
        ReminderDraft restored = ReminderDraft.restore(state);

        assertThat(restored.time).isNull();
        assertThat(restored.customRule).isNull();
        assertThat(restored.pickedDateUtc).isNull();
    }

    @Test
    public void aFreshSheet_hasNoDraft() {
        assertThat(ReminderDraft.restore(null)).isNull();
        assertThat(ReminderDraft.restore(new Bundle())).isNull();
    }

    @Test
    public void aPickedDayIsThatDayInTheLocalZone_alsoWestOfUtc() {
        // 2026-10-05 00:00 UTC, as the date picker gives it.
        long picked = 1_791_158_400_000L;
        TimeZone newYork = TimeZone.getTimeZone("America/New_York");
        long at = ReminderDraft.at(picked, 9, 30, newYork);

        java.util.Calendar local = java.util.Calendar.getInstance(newYork);
        local.setTimeInMillis(at);
        assertThat(local.get(java.util.Calendar.DAY_OF_MONTH)).isEqualTo(5);
        assertThat(local.get(java.util.Calendar.HOUR_OF_DAY)).isEqualTo(9);
        assertThat(local.get(java.util.Calendar.MINUTE)).isEqualTo(30);
    }
}
