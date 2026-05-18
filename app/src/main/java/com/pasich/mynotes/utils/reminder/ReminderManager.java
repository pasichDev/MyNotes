package com.pasich.mynotes.utils.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.ReminderRepeat;
import com.pasich.mynotes.ui.receiver.ReminderReceiver;
import java.util.Calendar;
import java.util.List;

public class ReminderManager {

    public static final String EXTRA_NOTE_ID = "noteId";
    public static final String EXTRA_NOTE_TITLE = "noteTitle";
    public static final String EXTRA_NOTE_PREVIEW = "notePreview";
    public static final String EXTRA_NOTE_REPEAT = "noteRepeat";
    public static final String EXTRA_NOTE_INTERVAL_MINUTES = "intervalMinutes";

    public static void scheduleReminder(Context ctx, Note note) {
        if (note.getReminderTime() == null) return;

        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) return;
        }

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, note.getReminderTime(), buildPendingIntent(ctx, note));
    }

    public static void cancelReminder(Context ctx, int noteId) {
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Intent intent = new Intent(ctx, ReminderReceiver.class);
        PendingIntent pi =
                PendingIntent.getBroadcast(
                        ctx,
                        noteId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pi);
        pi.cancel();
    }

    public static void rescheduleAll(Context ctx, List<Note> notes) {
        for (Note note : notes) {
            scheduleReminder(ctx, note);
        }
    }

    public static long computeNextTime(long from, ReminderRepeat repeat) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(from);
        switch (repeat) {
            case DAILY:
                cal.add(Calendar.DAY_OF_YEAR, 1);
                break;
            case WEEKLY:
                cal.add(Calendar.WEEK_OF_YEAR, 1);
                break;
            case MONTHLY:
                cal.add(Calendar.MONTH, 1);
                break;
            default:
                break;
        }
        return cal.getTimeInMillis();
    }

    public static boolean canScheduleExact(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    private static PendingIntent buildPendingIntent(Context ctx, Note note) {
        Intent intent = new Intent(ctx, ReminderReceiver.class);
        intent.putExtra(EXTRA_NOTE_ID, note.getId());
        intent.putExtra(EXTRA_NOTE_TITLE, note.getTitle());
        intent.putExtra(EXTRA_NOTE_PREVIEW, note.getValuePreview());
        intent.putExtra(EXTRA_NOTE_REPEAT, note.getReminderRepeat());
        intent.putExtra(EXTRA_NOTE_INTERVAL_MINUTES, note.getReminderIntervalMinutes());
        return PendingIntent.getBroadcast(
                ctx,
                note.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
