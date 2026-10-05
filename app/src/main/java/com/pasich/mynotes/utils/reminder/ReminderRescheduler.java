package com.pasich.mynotes.utils.reminder;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.RepeatRule;
import com.pasich.mynotes.data.model.Task;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.Collection;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Brings the armed alarms back in line with the reminders stored in the database.
 *
 * <p>Alarms do not survive a reboot, an app update, a force stop or the "Alarms & reminders"
 * permission being switched off and on, and a reminder changed on another device arrives by sync
 * without any alarm. This re-arms from what is stored, at boot, after an update, when the
 * permission is granted, at app start and after a sync.
 *
 * <p>An occurrence that passed while nothing was armed is armed for "now": the receiver posts one
 * notification for it and moves a repeating reminder to its next future occurrence counted from the
 * anchor, however many periods were missed. Only the receiver advances a schedule, so this running
 * at the same moment as a due alarm cannot skip or double it. A one-time reminder missed by more
 * than {@link #MISSED_ONE_TIME_GRACE_MS} is left alone, as before, rather than surfacing something
 * long out of date.
 */
@Singleton
public class ReminderRescheduler {

    static final long MISSED_ONE_TIME_GRACE_MS = 24L * 60 * 60 * 1000;

    /** What a full re-arm did. */
    public static final class Result {
        public final int armed;
        public final int inexact;

        Result(int armed, int inexact) {
            this.armed = armed;
            this.inexact = inexact;
        }
    }

    private final Context context;
    private final AppDatabase database;

    @Inject
    public ReminderRescheduler(@ApplicationContext Context context, AppDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
    }

    /** Re-arms every note reminder, task reminder and remembered snooze. */
    @WorkerThread
    @NonNull
    public synchronized Result rescheduleAll(long now) {
        int armed = 0;
        int inexact = 0;
        for (Note note : database.noteDao().getNotesWithRemindersSync()) {
            if (!shouldArm(note, now)) continue;
            armed++;
            if (!ReminderManager.scheduleReminder(context, note)) inexact++;
        }
        SnoozeStore snoozes = new SnoozeStore(context);
        for (SnoozeStore.Entry snooze : snoozes.all()) {
            Note note = database.noteDao().getNoteSync(snooze.noteId);
            if (note == null || note.isTrash()) {
                snoozes.remove(snooze.noteId);
                continue;
            }
            armed++;
            if (!ReminderManager.armSnooze(
                    context, snooze.noteId, snooze.time, snooze.intervalMinutes)) inexact++;
        }
        List<Task> tasks = database.taskDao().getTasksWithRemindersSync();
        TaskReminderManager.rescheduleAll(context, tasks);
        return new Result(armed, inexact);
    }

    /**
     * Arms or cancels the given notes' reminders to match the database, after sync wrote them: a
     * reminder set, moved or turned off on another device, or a note deleted there.
     */
    @WorkerThread
    public synchronized void reconcileNotes(@NonNull Collection<Integer> noteIds, long now) {
        for (int noteId : noteIds) {
            Note note = database.noteDao().getNoteSync(noteId);
            if (note == null || note.isTrash()) {
                ReminderManager.cancelReminder(context, noteId);
            } else if (note.getReminderTime() == null) {
                // Turned off, or a one-time reminder that already fired: a snooze or repeating
                // notification of what was already shown stays.
                ReminderManager.cancelScheduled(context, noteId);
            } else if (shouldArm(note, now)) {
                ReminderManager.scheduleReminder(context, note);
            }
        }
    }

    /** How many notes have a reminder still ahead, for asking about exact alarms. */
    @WorkerThread
    public int countUpcoming(long now) {
        return database.noteDao().countUpcomingRemindersSync(now);
    }

    static boolean shouldArm(@NonNull Note note, long now) {
        Long time = note.getReminderTime();
        if (time == null) return false;
        if (time > now) return true;
        if (RepeatRule.parse(note.getReminderRepeat()).isRepeating()) return true;
        return now - time <= MISSED_ONE_TIME_GRACE_MS;
    }
}
