package com.pasich.mynotes.data.model;

import static com.google.common.truth.Truth.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RepeatRuleTest {

    /** Kyiv moves clocks forward on 30 Mar 2025 at 03:00 and back on 26 Oct 2025 at 04:00. */
    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static long at(ZoneId zone, int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli();
    }

    private static LocalDateTime local(long millis, ZoneId zone) {
        return java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime();
    }

    // --- parse -----------------------------------------------------------------------------

    @Test
    public void parse_legacyValues() {
        assertThat(RepeatRule.parse("DAILY"))
                .isEqualTo(RepeatRule.every(1, RepeatRule.Unit.DAYS, null));
        assertThat(RepeatRule.parse("WEEKLY"))
                .isEqualTo(RepeatRule.every(1, RepeatRule.Unit.WEEKS, null));
        assertThat(RepeatRule.parse("MONTHLY"))
                .isEqualTo(RepeatRule.every(1, RepeatRule.Unit.MONTHS, null));
        assertThat(RepeatRule.parse("NONE")).isEqualTo(RepeatRule.NONE);
    }

    @Test
    public void parse_customRuleWithAnchor() {
        RepeatRule rule = RepeatRule.parse("EVERY:3:HOURS@1767225600000");
        assertThat(rule.isRepeating()).isTrue();
        assertThat(rule.getInterval()).isEqualTo(3);
        assertThat(rule.getUnit()).isEqualTo(RepeatRule.Unit.HOURS);
        assertThat(rule.getAnchor()).isEqualTo(1767225600000L);
    }

    @Test
    public void parse_customRuleWithoutAnchor_usesReminderTime() {
        RepeatRule rule = RepeatRule.parse("EVERY:2:YEARS");
        assertThat(rule.getUnit()).isEqualTo(RepeatRule.Unit.YEARS);
        assertThat(rule.getAnchor()).isNull();
        assertThat(rule.anchorOr(42L)).isEqualTo(42L);
    }

    @Test
    public void parse_garbage_isNone() {
        String[] garbage = {
            null,
            "",
            "GARBAGE",
            "daily",
            "EVERY:",
            "EVERY:3",
            "EVERY::HOURS",
            "EVERY:x:DAYS",
            "EVERY:0:DAYS",
            "EVERY:-1:DAYS",
            "EVERY:1000:DAYS",
            "EVERY:3:FORTNIGHTS",
            "EVERY:3:hours",
            "EVERY:3:HOURS@",
            "EVERY:3:HOURS@soon",
            "every:3:HOURS",
        };
        for (String value : garbage) {
            assertThat(RepeatRule.parse(value)).isEqualTo(RepeatRule.NONE);
            assertThat(RepeatRule.parse(value).isRepeating()).isFalse();
        }
    }

    // --- serialize -------------------------------------------------------------------------

    @Test
    public void serialize_everyUnitRoundTrips() {
        long anchor = at(UTC, 2026, 1, 31, 9, 0);
        for (RepeatRule.Unit unit : RepeatRule.Unit.values()) {
            for (int n : new int[] {1, 2, 7, 999}) {
                RepeatRule rule = RepeatRule.every(n, unit, anchor);
                RepeatRule back = RepeatRule.parse(rule.serialize(UTC));
                assertThat(back.getInterval()).isEqualTo(n);
                assertThat(back.getUnit()).isEqualTo(unit);
                // The legacy form drops the anchor; the reminder's own time stands in for it.
                assertThat(back.anchorOr(anchor)).isEqualTo(anchor);
            }
        }
    }

    @Test
    public void serialize_writesLegacyValuesWhereOlderVersionsUnderstandThem() {
        long the15th = at(UTC, 2026, 1, 15, 9, 0);
        assertThat(RepeatRule.every(1, RepeatRule.Unit.DAYS, the15th).serialize(UTC))
                .isEqualTo("DAILY");
        assertThat(RepeatRule.every(1, RepeatRule.Unit.WEEKS, the15th).serialize(UTC))
                .isEqualTo("WEEKLY");
        assertThat(RepeatRule.every(1, RepeatRule.Unit.MONTHS, the15th).serialize(UTC))
                .isEqualTo("MONTHLY");
        assertThat(RepeatRule.NONE.serialize(UTC)).isEqualTo("NONE");
    }

    @Test
    public void serialize_keepsTheAnchorWhenLegacyWouldLoseIt() {
        long the31st = at(UTC, 2026, 1, 31, 9, 0);
        assertThat(RepeatRule.every(1, RepeatRule.Unit.MONTHS, the31st).serialize(UTC))
                .isEqualTo("EVERY:1:MONTHS@" + the31st);
        assertThat(RepeatRule.every(1, RepeatRule.Unit.HOURS, the31st).serialize(UTC))
                .isEqualTo("EVERY:1:HOURS@" + the31st);
        assertThat(RepeatRule.every(1, RepeatRule.Unit.YEARS, the31st).serialize(UTC))
                .isEqualTo("EVERY:1:YEARS@" + the31st);
        assertThat(RepeatRule.every(3, RepeatRule.Unit.DAYS, the31st).serialize(UTC))
                .isEqualTo("EVERY:3:DAYS@" + the31st);
    }

    @Test
    public void customRule_isOneTimeForOlderVersions() {
        String stored = RepeatRule.every(3, RepeatRule.Unit.HOURS, 1L).serialize(UTC);
        assertThat(ReminderRepeat.from(stored)).isEqualTo(ReminderRepeat.NONE);
    }

    @Test
    public void every_clampsTheInterval() {
        assertThat(RepeatRule.every(0, RepeatRule.Unit.DAYS, null).getInterval()).isEqualTo(1);
        assertThat(RepeatRule.every(5000, RepeatRule.Unit.DAYS, null).getInterval()).isEqualTo(999);
    }

    // --- next ------------------------------------------------------------------------------

    @Test
    public void next_noneDoesNotRepeat() {
        assertThat(RepeatRule.NONE.next(0L, 10L, UTC)).isEqualTo(-1L);
    }

    @Test
    public void next_anchorStillAhead_isTheAnchor() {
        RepeatRule rule = RepeatRule.every(2, RepeatRule.Unit.DAYS, null);
        assertThat(rule.next(1_000L, 10L, UTC)).isEqualTo(1_000L);
    }

    @Test
    public void next_isStrictlyAfter() {
        long anchor = at(UTC, 2026, 3, 1, 9, 0);
        RepeatRule rule = RepeatRule.every(3, RepeatRule.Unit.HOURS, anchor);
        assertThat(rule.next(anchor, anchor, UTC))
                .isEqualTo(anchor + Duration.ofHours(3).toMillis());
    }

    @Test
    public void next_everyUnitStepsFromTheAnchor() {
        long anchor = at(UTC, 2026, 1, 10, 9, 0);
        assertThat(
                        local(
                                RepeatRule.every(5, RepeatRule.Unit.HOURS, anchor)
                                        .next(anchor, anchor, UTC),
                                UTC))
                .isEqualTo(LocalDateTime.of(2026, 1, 10, 14, 0));
        assertThat(
                        local(
                                RepeatRule.every(2, RepeatRule.Unit.DAYS, anchor)
                                        .next(anchor, anchor, UTC),
                                UTC))
                .isEqualTo(LocalDateTime.of(2026, 1, 12, 9, 0));
        assertThat(
                        local(
                                RepeatRule.every(3, RepeatRule.Unit.WEEKS, anchor)
                                        .next(anchor, anchor, UTC),
                                UTC))
                .isEqualTo(LocalDateTime.of(2026, 1, 31, 9, 0));
        assertThat(
                        local(
                                RepeatRule.every(4, RepeatRule.Unit.MONTHS, anchor)
                                        .next(anchor, anchor, UTC),
                                UTC))
                .isEqualTo(LocalDateTime.of(2026, 5, 10, 9, 0));
        assertThat(
                        local(
                                RepeatRule.every(2, RepeatRule.Unit.YEARS, anchor)
                                        .next(anchor, anchor, UTC),
                                UTC))
                .isEqualTo(LocalDateTime.of(2028, 1, 10, 9, 0));
    }

    @Test
    public void next_monthlyFromThe31st_returnsToThe31st() {
        long anchor = at(KYIV, 2025, 1, 31, 9, 0);
        RepeatRule rule = RepeatRule.every(1, RepeatRule.Unit.MONTHS, anchor);
        List<LocalDate> dates = new ArrayList<>();
        long t = anchor;
        for (int i = 0; i < 13; i++) {
            t = rule.next(anchor, t, KYIV);
            dates.add(local(t, KYIV).toLocalDate());
            assertThat(local(t, KYIV).toLocalTime()).isEqualTo(java.time.LocalTime.of(9, 0));
        }
        assertThat(dates)
                .containsExactly(
                        LocalDate.of(2025, 2, 28),
                        LocalDate.of(2025, 3, 31),
                        LocalDate.of(2025, 4, 30),
                        LocalDate.of(2025, 5, 31),
                        LocalDate.of(2025, 6, 30),
                        LocalDate.of(2025, 7, 31),
                        LocalDate.of(2025, 8, 31),
                        LocalDate.of(2025, 9, 30),
                        LocalDate.of(2025, 10, 31),
                        LocalDate.of(2025, 11, 30),
                        LocalDate.of(2025, 12, 31),
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 2, 28))
                .inOrder();
    }

    @Test
    public void next_monthlyFromThe31st_inALeapYear_landsOnThe29th() {
        long anchor = at(UTC, 2024, 1, 31, 9, 0);
        RepeatRule rule = RepeatRule.every(1, RepeatRule.Unit.MONTHS, anchor);
        assertThat(local(rule.next(anchor, anchor, UTC), UTC).toLocalDate())
                .isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    public void next_yearlyFrom29February_overEightYears() {
        long anchor = at(KYIV, 2024, 2, 29, 8, 30);
        RepeatRule rule = RepeatRule.every(1, RepeatRule.Unit.YEARS, anchor);
        List<LocalDate> dates = new ArrayList<>();
        long t = anchor;
        for (int i = 0; i < 8; i++) {
            t = rule.next(anchor, t, KYIV);
            dates.add(local(t, KYIV).toLocalDate());
        }
        assertThat(dates)
                .containsExactly(
                        LocalDate.of(2025, 2, 28),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2027, 2, 28),
                        LocalDate.of(2028, 2, 29),
                        LocalDate.of(2029, 2, 28),
                        LocalDate.of(2030, 2, 28),
                        LocalDate.of(2031, 2, 28),
                        LocalDate.of(2032, 2, 29))
                .inOrder();
    }

    @Test
    public void next_hoursAreElapsedTimeAcrossDst() {
        // Midnight before the spring-forward night: 24 one-hour steps are 24 real hours, so the
        // last lands at 01:00 local, not midnight.
        long anchor = at(KYIV, 2025, 3, 30, 0, 0);
        RepeatRule rule = RepeatRule.every(1, RepeatRule.Unit.HOURS, anchor);
        long t = anchor;
        for (int i = 0; i < 24; i++) {
            long next = rule.next(anchor, t, KYIV);
            assertThat(next - t).isEqualTo(Duration.ofHours(1).toMillis());
            t = next;
        }
        assertThat(local(t, KYIV)).isEqualTo(LocalDateTime.of(2025, 3, 31, 1, 0));

        RepeatRule every24h = RepeatRule.every(24, RepeatRule.Unit.HOURS, anchor);
        assertThat(local(every24h.next(anchor, anchor, KYIV), KYIV))
                .isEqualTo(LocalDateTime.of(2025, 3, 31, 1, 0));
    }

    @Test
    public void next_daysKeepTheWallClockAcrossDst() {
        long springAnchor = at(KYIV, 2025, 3, 29, 9, 0);
        RepeatRule daily = RepeatRule.every(1, RepeatRule.Unit.DAYS, springAnchor);
        long spring = daily.next(springAnchor, springAnchor, KYIV);
        assertThat(local(spring, KYIV)).isEqualTo(LocalDateTime.of(2025, 3, 30, 9, 0));
        assertThat(spring - springAnchor).isEqualTo(Duration.ofHours(23).toMillis());

        long autumnAnchor = at(KYIV, 2025, 10, 25, 9, 0);
        RepeatRule weekly = RepeatRule.every(1, RepeatRule.Unit.WEEKS, autumnAnchor);
        long autumn = weekly.next(autumnAnchor, autumnAnchor, KYIV);
        assertThat(local(autumn, KYIV)).isEqualTo(LocalDateTime.of(2025, 11, 1, 9, 0));
        assertThat(autumn - autumnAnchor).isEqualTo(Duration.ofHours(7 * 24 + 1).toMillis());
    }

    @Test
    public void next_aTimeSkippedByDst_returnsToItsWallClockAfterwards() {
        // 03:30 does not exist on 30 March in Kyiv; that day rings an hour later, the next day
        // is back at 03:30 because each occurrence is counted from the anchor.
        long anchor = at(KYIV, 2025, 3, 29, 3, 30);
        RepeatRule daily = RepeatRule.every(1, RepeatRule.Unit.DAYS, anchor);
        long gapDay = daily.next(anchor, anchor, KYIV);
        assertThat(local(gapDay, KYIV)).isEqualTo(LocalDateTime.of(2025, 3, 30, 4, 30));
        assertThat(local(daily.next(anchor, gapDay, KYIV), KYIV))
                .isEqualTo(LocalDateTime.of(2025, 3, 31, 3, 30));
    }

    @Test
    public void next_catchesUpAfterTenMissedPeriods_inOneStep() {
        long anchor = at(KYIV, 2025, 1, 1, 7, 15);
        for (RepeatRule.Unit unit : RepeatRule.Unit.values()) {
            RepeatRule rule = RepeatRule.every(3, unit, anchor);
            long tenth = rule.occurrence(anchor, 10, KYIV);
            long eleventh = rule.occurrence(anchor, 11, KYIV);
            // The phone was off from just after the anchor until just after the 10th period.
            long now = tenth + Duration.ofMinutes(5).toMillis();
            assertThat(rule.next(anchor, now, KYIV)).isEqualTo(eleventh);
        }
    }

    @Test
    public void next_doesNotDriftWhenDeliveredLate() {
        long anchor = at(UTC, 2026, 1, 1, 9, 0);
        RepeatRule rule = RepeatRule.every(1, RepeatRule.Unit.DAYS, anchor);
        // Delivered 20 minutes late each day: the next time is still 09:00.
        long late = anchor + Duration.ofMinutes(20).toMillis();
        assertThat(local(rule.next(anchor, late, UTC), UTC))
                .isEqualTo(LocalDateTime.of(2026, 1, 2, 9, 0));
    }

    @Test
    public void next_longGapsAreCheap() {
        long anchor = at(UTC, 2000, 1, 1, 0, 0);
        RepeatRule hourly = RepeatRule.every(1, RepeatRule.Unit.HOURS, anchor);
        long now = at(UTC, 2026, 6, 15, 12, 30);
        assertThat(local(hourly.next(anchor, now, UTC), UTC))
                .isEqualTo(LocalDateTime.of(2026, 6, 15, 13, 0));
        RepeatRule monthly =
                RepeatRule.every(1, RepeatRule.Unit.MONTHS, at(UTC, 2000, 1, 31, 9, 0));
        assertThat(local(monthly.next(monthly.getAnchor(), now, UTC), UTC))
                .isEqualTo(LocalDateTime.of(2026, 6, 30, 9, 0));
    }
}
