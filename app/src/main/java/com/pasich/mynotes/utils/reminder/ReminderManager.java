package com.pasich.mynotes.utils.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.ui.receiver.ReminderReceiver;
import java.util.List;

/**
 * Arms and cancels the alarms behind a note reminder.
 *
 * <p>A note has up to three independent alarms, told apart by their intent action so that none
 * replaces another:
 *
 * <ul>
 *   <li>the scheduled occurrence (no action, as in earlier versions, so an alarm armed before an
 *       update is still the one that is replaced or cancelled);
 *   <li>the "repeat notification" nag ({@link #ACTION_NAG}), which re-posts the notification every
 *       few minutes until it is dismissed and never moves the schedule;
 *   <li>a snooze ({@link #ACTION_SNOOZE}), remembered in {@link SnoozeStore} so a reboot keeps it.
 * </ul>
 *
 * <p>When the system does not allow exact alarms the reminder is still armed, as an inexact alarm
 * that may arrive a little late, rather than silently dropped; the caller is told so it can ask for
 * the permission.
 */
public final class ReminderManager {

    public static final String EXTRA_NOTE_ID = "noteId";
    public static final String EXTRA_NOTE_TITLE = "noteTitle";
    public static final String EXTRA_NOTE_PREVIEW = "notePreview";
    public static final String EXTRA_NOTE_REPEAT = "noteRepeat";
    public static final String EXTRA_NOTE_INTERVAL_MINUTES = "intervalMinutes";

    /** The time the alarm was armed for, so the receiver can tell a stale alarm from a due one. */
    public static final String EXTRA_SCHEDULED_AT = "scheduledAt";

    public static final String ACTION_NAG = "com.pasich.mynotes.ACTION_REMINDER_NAG";
    public static final String ACTION_SNOOZE = "com.pasich.mynotes.ACTION_REMINDER_SNOOZE";

    private ReminderManager() {}

    /**
     * Arms the note's next occurrence at {@link Note#getReminderTime()}. A time already passed
     * fires at once, which is how a missed occurrence is caught up.
     *
     * @return true when the alarm is exact, false when it was armed inexactly or not at all
     */
    public static boolean scheduleReminder(@NonNull Context ctx, @NonNull Note note) {
        Long time = note.getReminderTime();
        if (time == null) return false;
        Intent intent = baseIntent(ctx, note.getId());
        intent.putExtra(EXTRA_NOTE_TITLE, note.getTitle());
        intent.putExtra(EXTRA_NOTE_PREVIEW, note.getValuePreview());
        intent.putExtra(EXTRA_NOTE_REPEAT, note.getReminderRepeat());
        intent.putExtra(EXTRA_NOTE_INTERVAL_MINUTES, note.getReminderIntervalMinutes());
        intent.putExtra(EXTRA_SCHEDULED_AT, time);
        return arm(ctx, time, broadcast(ctx, note.getId(), intent));
    }

    /** Re-posts the notification after {@code intervalMinutes} until it is dismissed. */
    public static boolean scheduleNag(
            @NonNull Context ctx, int noteId, int intervalMinutes, long now) {
        if (intervalMinutes <= 0) return false;
        Intent intent = baseIntent(ctx, noteId).setAction(ACTION_NAG);
        intent.putExtra(EXTRA_NOTE_INTERVAL_MINUTES, intervalMinutes);
        return arm(ctx, now + intervalMinutes * 60_000L, broadcast(ctx, noteId, intent));
    }

    /** Stops the "repeat notification" cycle; the schedule itself is untouched. */
    public static void cancelNag(@NonNull Context ctx, int noteId) {
        cancel(ctx, noteId, ACTION_NAG);
    }

    /**
     * Shows the reminder again at {@code time}, without touching the repeat schedule, and remembers
     * it so a reboot does not lose it.
     */
    public static boolean scheduleSnooze(
            @NonNull Context ctx, int noteId, long time, int intervalMinutes) {
        new SnoozeStore(ctx).put(noteId, time, intervalMinutes);
        return armSnooze(ctx, noteId, time, intervalMinutes);
    }

    static boolean armSnooze(@NonNull Context ctx, int noteId, long time, int intervalMinutes) {
        Intent intent = baseIntent(ctx, noteId).setAction(ACTION_SNOOZE);
        intent.putExtra(EXTRA_NOTE_INTERVAL_MINUTES, intervalMinutes);
        intent.putExtra(EXTRA_SCHEDULED_AT, time);
        return arm(ctx, time, broadcast(ctx, noteId, intent));
    }

    /**
     * Cancels the scheduled occurrence only. A notification already shown keeps its repeat cycle
     * and its snooze: they answer something that already happened.
     */
    public static void cancelScheduled(@NonNull Context ctx, int noteId) {
        cancel(ctx, noteId, null);
    }

    /** Cancels everything armed for the note: the schedule, the nag and any snooze. */
    public static void cancelReminder(@NonNull Context ctx, int noteId) {
        cancel(ctx, noteId, null);
        cancel(ctx, noteId, ACTION_NAG);
        cancel(ctx, noteId, ACTION_SNOOZE);
        new SnoozeStore(ctx).remove(noteId);
    }

    /** Re-arms the given notes' scheduled occurrences. */
    public static void rescheduleAll(@NonNull Context ctx, @NonNull List<Note> notes) {
        for (Note note : notes) scheduleReminder(ctx, note);
    }

    /** Whether the app may schedule exact alarms ("Alarms & reminders" on Android 12+). */
    public static boolean canScheduleExact(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    /**
     * The system screen where the user allows exact alarms for this app, or null below Android 12,
     * where no permission is needed.
     */
    @Nullable
    public static Intent exactAlarmSettingsIntent(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null;
        return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", ctx.getPackageName(), null));
    }

    static boolean arm(@NonNull Context ctx, long time, @NonNull PendingIntent pi) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;
        if (canScheduleExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi);
            return true;
        }
        // Allowed without the permission; the system may deliver it some minutes late.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi);
        return false;
    }

    private static void cancel(@NonNull Context ctx, int noteId, @Nullable String action) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent intent = baseIntent(ctx, noteId);
        if (action != null) intent.setAction(action);
        PendingIntent pi = broadcast(ctx, noteId, intent);
        if (am != null) am.cancel(pi);
        pi.cancel();
    }

    private static Intent baseIntent(@NonNull Context ctx, int noteId) {
        return new Intent(ctx, ReminderReceiver.class).putExtra(EXTRA_NOTE_ID, noteId);
    }

    private static PendingIntent broadcast(@NonNull Context ctx, int noteId, Intent intent) {
        return PendingIntent.getBroadcast(
                ctx,
                noteId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
