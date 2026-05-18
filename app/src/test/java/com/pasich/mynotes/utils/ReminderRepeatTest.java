package com.pasich.mynotes.utils;

import static org.junit.Assert.assertEquals;

import com.pasich.mynotes.data.model.ReminderRepeat;
import org.junit.Test;

public class ReminderRepeatTest {

    @Test
    public void from_null_returnsNone() {
        assertEquals(ReminderRepeat.NONE, ReminderRepeat.from(null));
    }

    @Test
    public void from_emptyString_returnsNone() {
        assertEquals(ReminderRepeat.NONE, ReminderRepeat.from(""));
    }

    @Test
    public void from_unknownString_returnsNone() {
        assertEquals(ReminderRepeat.NONE, ReminderRepeat.from("GARBAGE"));
    }

    @Test
    public void from_noneString_returnsNone() {
        assertEquals(ReminderRepeat.NONE, ReminderRepeat.from("NONE"));
    }

    @Test
    public void from_daily_returnsDaily() {
        assertEquals(ReminderRepeat.DAILY, ReminderRepeat.from("DAILY"));
    }

    @Test
    public void from_weekly_returnsWeekly() {
        assertEquals(ReminderRepeat.WEEKLY, ReminderRepeat.from("WEEKLY"));
    }

    @Test
    public void from_monthly_returnsMonthly() {
        assertEquals(ReminderRepeat.MONTHLY, ReminderRepeat.from("MONTHLY"));
    }

    @Test
    public void from_lowercaseDaily_returnsNone() {
        // Case-sensitive: lowercase is not a valid enum value
        assertEquals(ReminderRepeat.NONE, ReminderRepeat.from("daily"));
    }
}
