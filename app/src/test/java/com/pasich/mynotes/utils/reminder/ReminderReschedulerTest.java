package com.pasich.mynotes.utils.reminder;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import androidx.room.Room;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.RepeatRule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ReminderReschedulerTest {

    private static final long HOUR = 3_600_000L;
    private static final long NOW = 1_780_000_000_000L;

    private Context context;
    private AppDatabase db;
    private ReminderRescheduler rescheduler;
    private ShadowAlarmManager alarms;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        db =
                Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                        .allowMainThreadQueries()
                        .build();
        rescheduler = new ReminderRescheduler(context, db);
        alarms = shadowOf((AlarmManager) context.getSystemService(Context.ALARM_SERVICE));
        ShadowAlarmManager.setCanScheduleExactAlarms(true);
    }

    @After
    public void tearDown() {
        db.close();
        ShadowAlarmManager.reset();
        context.getSharedPreferences("reminder_snoozes", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    private int note(Long time, String repeat, boolean trash) {
        Note note = new Note().create("Title", "Body", NOW);
        note.setReminderTime(time);
        note.setReminderRepeat(repeat);
        note.setTrash(trash);
        return db.noteDao().addNote(note).intValue();
    }

    private List<ShadowAlarmManager.ScheduledAlarm> alarmsFor(int noteId, String action) {
        List<ShadowAlarmManager.ScheduledAlarm> found = new ArrayList<>();
        for (ShadowAlarmManager.ScheduledAlarm alarm : alarms.getScheduledAlarms()) {
            Intent intent = shadowOf(alarm.operation).getSavedIntent();
            if (intent.getIntExtra(ReminderManager.EXTRA_NOTE_ID, -1) != noteId) continue;
            String a = intent.getAction();
            if (action == null ? a == null : action.equals(a)) found.add(alarm);
        }
        return found;
    }

    @Test
    public void rescheduleAll_armsUpcomingReminders() {
        int id = note(NOW + 2 * HOUR, "NONE", false);

        ReminderRescheduler.Result result = rescheduler.rescheduleAll(NOW);

        assertThat(alarmsFor(id, null)).hasSize(1);
        assertThat(alarmsFor(id, null).get(0).getTriggerAtMs()).isEqualTo(NOW + 2 * HOUR);
        assertThat(alarmsFor(id, null).get(0).getWindowLengthMs())
                .isEqualTo(ShadowAlarmManager.WINDOW_EXACT);
        assertThat(result.armed).isEqualTo(1);
        assertThat(result.inexact).isEqualTo(0);
    }

    @Test
    public void rescheduleAll_armsAMissedRepeatingReminderForNow() {
        // Missed while the phone was off: armed at its own, past, time so it fires at once and
        // the receiver posts it and rolls it forward from the anchor.
        long missed = NOW - 3 * 24 * HOUR;
        int daily = note(missed, "DAILY", false);
        int custom =
                note(missed, RepeatRule.every(3, RepeatRule.Unit.HOURS, missed).serialize(), false);

        rescheduler.rescheduleAll(NOW);

        assertThat(alarmsFor(daily, null)).hasSize(1);
        assertThat(alarmsFor(daily, null).get(0).getTriggerAtMs()).isEqualTo(missed);
        assertThat(alarmsFor(custom, null)).hasSize(1);
    }

    @Test
    public void rescheduleAll_oneTimeReminders_onlyRecentlyMissedOnesAreCaughtUp() {
        int recent = note(NOW - HOUR, "NONE", false);
        int stale = note(NOW - 3 * 24 * HOUR, "NONE", false);

        rescheduler.rescheduleAll(NOW);

        assertThat(alarmsFor(recent, null)).hasSize(1);
        assertThat(alarmsFor(stale, null)).isEmpty();
    }

    @Test
    public void rescheduleAll_skipsTrashAndNotesWithoutReminder() {
        int trashed = note(NOW + HOUR, "DAILY", true);
        int none = note(null, "NONE", false);

        rescheduler.rescheduleAll(NOW);

        assertThat(alarmsFor(trashed, null)).isEmpty();
        assertThat(alarmsFor(none, null)).isEmpty();
    }

    @Test
    public void rescheduleAll_restoresSnoozes_andForgetsThoseOfDeletedNotes() {
        int id = note(NOW + 5 * HOUR, "DAILY", false);
        SnoozeStore snoozes = new SnoozeStore(context);
        snoozes.put(id, NOW + HOUR, 10);
        snoozes.put(9_999, NOW + HOUR, 0);

        rescheduler.rescheduleAll(NOW);

        List<ShadowAlarmManager.ScheduledAlarm> snooze =
                alarmsFor(id, ReminderManager.ACTION_SNOOZE);
        assertThat(snooze).hasSize(1);
        assertThat(snooze.get(0).getTriggerAtMs()).isEqualTo(NOW + HOUR);
        // The snooze has its own alarm; the schedule is still armed beside it.
        assertThat(alarmsFor(id, null)).hasSize(1);
        assertThat(snoozes.get(9_999)).isNull();
        assertThat(snoozes.get(id)).isNotNull();
    }

    @Test
    public void rescheduleAll_withoutExactAlarms_armsInexactlyAndSaysSo() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false);
        int id = note(NOW + HOUR, "NONE", false);

        ReminderRescheduler.Result result = rescheduler.rescheduleAll(NOW);

        assertThat(alarmsFor(id, null)).hasSize(1);
        assertThat(alarmsFor(id, null).get(0).getWindowLengthMs())
                .isNotEqualTo(ShadowAlarmManager.WINDOW_EXACT);
        assertThat(result.inexact).isEqualTo(1);
    }

    @Test
    public void rescheduleAll_isIdempotent() {
        int id = note(NOW + HOUR, "WEEKLY", false);

        rescheduler.rescheduleAll(NOW);
        rescheduler.rescheduleAll(NOW);

        assertThat(alarmsFor(id, null)).hasSize(1);
    }

    @Test
    public void nagAndSnooze_doNotReplaceTheSchedule() {
        int id = note(NOW + 24 * HOUR, "DAILY", false);
        rescheduler.rescheduleAll(NOW);

        ReminderManager.scheduleNag(context, id, 10, NOW);
        ReminderManager.scheduleSnooze(context, id, NOW + HOUR, 0);

        assertThat(alarmsFor(id, null)).hasSize(1);
        assertThat(alarmsFor(id, null).get(0).getTriggerAtMs()).isEqualTo(NOW + 24 * HOUR);
        assertThat(alarmsFor(id, ReminderManager.ACTION_NAG)).hasSize(1);
        assertThat(alarmsFor(id, ReminderManager.ACTION_NAG).get(0).getTriggerAtMs())
                .isEqualTo(NOW + 10 * 60_000L);
        assertThat(alarmsFor(id, ReminderManager.ACTION_SNOOZE)).hasSize(1);

        ReminderManager.cancelNag(context, id);
        assertThat(alarmsFor(id, ReminderManager.ACTION_NAG)).isEmpty();
        assertThat(alarmsFor(id, null)).hasSize(1);
    }

    @Test
    public void reconcileNotes_followsWhatSyncWrote() {
        int moved = note(NOW + HOUR, "DAILY", false);
        int cleared = note(NOW + HOUR, "NONE", false);
        int deleted = note(NOW + HOUR, "NONE", false);
        rescheduler.rescheduleAll(NOW);
        ReminderManager.scheduleSnooze(context, cleared, NOW + 2 * HOUR, 0);
        ReminderManager.scheduleSnooze(context, deleted, NOW + 2 * HOUR, 0);

        // Another device moved one reminder, turned one off and deleted a note.
        db.noteDao().updateReminderFullSync(moved, NOW + 5 * HOUR, "DAILY", 0);
        db.noteDao().clearReminderSync(cleared);
        db.noteDao().deleteById(deleted);
        rescheduler.reconcileNotes(Arrays.asList(moved, cleared, deleted), NOW);

        assertThat(alarmsFor(moved, null)).hasSize(1);
        assertThat(alarmsFor(moved, null).get(0).getTriggerAtMs()).isEqualTo(NOW + 5 * HOUR);
        assertThat(alarmsFor(cleared, null)).isEmpty();
        // A snooze of a notification already shown is kept until the note itself goes.
        assertThat(alarmsFor(cleared, ReminderManager.ACTION_SNOOZE)).hasSize(1);
        assertThat(alarmsFor(deleted, null)).isEmpty();
        assertThat(alarmsFor(deleted, ReminderManager.ACTION_SNOOZE)).isEmpty();
        assertThat(new SnoozeStore(context).get(deleted)).isNull();
    }

    @Test
    public void countUpcoming_countsOnlyFutureReminders() {
        note(NOW + HOUR, "NONE", false);
        note(NOW - HOUR, "DAILY", false);
        note(NOW + HOUR, "NONE", true);

        assertThat(rescheduler.countUpcoming(NOW)).isEqualTo(1);
    }
}
