package com.pasich.mynotes.ui.receiver;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;

import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.NotificationPreferencesCache;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.ReminderRepeat;
import com.pasich.mynotes.ui.view.activity.MainActivity;
import com.pasich.mynotes.ui.view.activity.SnoozeActivity;
import com.pasich.mynotes.ui.view.activity.noteEditor.NoteActivity;
import com.pasich.mynotes.utils.navigation.NoteExtras;
import com.pasich.mynotes.utils.reminder.ReminderManager;

import java.util.Calendar;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderReceiver";

    @Inject
    DataManager dataManager;

    @Inject
    NotificationPreferencesCache notificationPreferencesCache;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int noteId = intent.getIntExtra(ReminderManager.EXTRA_NOTE_ID, -1);
        String title = intent.getStringExtra(ReminderManager.EXTRA_NOTE_TITLE);
        String preview = intent.getStringExtra(ReminderManager.EXTRA_NOTE_PREVIEW);
        String repeatStr = intent.getStringExtra(ReminderManager.EXTRA_NOTE_REPEAT);
        int intervalMinutes = intent.getIntExtra(ReminderManager.EXTRA_NOTE_INTERVAL_MINUTES, 0);

        if (noteId == -1) return;

        showNotification(ctx, noteId, title, preview);

        if (intervalMinutes > 0) {
            long nextTime = System.currentTimeMillis() + intervalMinutes * 60_000L;
            dataManager.updateNoteReminderFull(noteId, nextTime,
                            repeatStr != null ? repeatStr : "NONE", intervalMinutes)
                    .subscribe(() -> {}, e -> Log.e(TAG, "updateReminderFull failed", e));
            Note tempNote = new Note();
            tempNote.setId(noteId);
            tempNote.setTitle(title != null ? title : "");
            tempNote.setValue(preview != null ? preview : "");
            tempNote.setReminderTime(nextTime);
            tempNote.setReminderRepeat(repeatStr != null ? repeatStr : "NONE");
            tempNote.setReminderIntervalMinutes(intervalMinutes);
            ReminderManager.scheduleReminder(ctx, tempNote);
            return;
        }

        ReminderRepeat repeat = ReminderRepeat.from(repeatStr);
        if (repeat == ReminderRepeat.NONE) {
            dataManager.clearReminder(noteId)
                    .subscribe(() -> {}, e -> Log.e(TAG, "clearReminder failed", e));
        } else {
            long nextTime = computeNextTime(System.currentTimeMillis(), repeat);
            dataManager.updateNoteReminderFull(noteId, nextTime, repeat.name(), 0)
                    .subscribe(() -> {}, e -> Log.e(TAG, "updateReminder failed", e));
            Note tempNote = new Note();
            tempNote.setId(noteId);
            tempNote.setTitle(title != null ? title : "");
            tempNote.setValue(preview != null ? preview : "");
            tempNote.setReminderTime(nextTime);
            tempNote.setReminderRepeat(repeat.name());
            tempNote.setReminderIntervalMinutes(0);
            ReminderManager.scheduleReminder(ctx, tempNote);
        }
    }

    private long computeNextTime(long from, ReminderRepeat repeat) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(from);
        switch (repeat) {
            case DAILY:   cal.add(Calendar.DAY_OF_YEAR, 1); break;
            case WEEKLY:  cal.add(Calendar.WEEK_OF_YEAR, 1); break;
            case MONTHLY: cal.add(Calendar.MONTH, 1); break;
            default: break;
        }
        return cal.getTimeInMillis();
    }

    private void showNotification(Context ctx, int noteId, String title, String preview) {
        Intent noteIntent = new Intent(ctx, NoteActivity.class);
        noteIntent.putExtra(NoteExtras.EXTRA_NEW_NOTE, false);
        noteIntent.putExtra(NoteExtras.EXTRA_ID_NOTE, (long) noteId);

        PendingIntent openPi = TaskStackBuilder.create(ctx)
                .addNextIntent(new Intent(ctx, MainActivity.class))
                .addNextIntent(noteIntent)
                .getPendingIntent(noteId, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent snoozeIntent = new Intent(ctx, SnoozeActivity.class);
        snoozeIntent.putExtra(ReminderManager.EXTRA_NOTE_ID, noteId);
        snoozeIntent.putExtra(ReminderManager.EXTRA_NOTE_TITLE, title);
        snoozeIntent.putExtra(ReminderManager.EXTRA_NOTE_PREVIEW, preview);
        snoozeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent snoozePi = PendingIntent.getActivity(
                ctx, noteId + 10000, snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String notifTitle = (title != null && !title.isEmpty()) ? title : ctx.getString(R.string.app_name);
        String notifText = (preview != null && preview.length() > 100)
                ? preview.substring(0, 100) : (preview != null ? preview : "");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                ctx, notificationPreferencesCache.getChannelId())
                .setSmallIcon(R.drawable.ic_bell_small)
                .setContentTitle(notifTitle)
                .setContentText(notifText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openPi)
                .addAction(0, ctx.getString(R.string.reminder_snooze_label), snoozePi);

        NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
        try {
            nm.notify(noteId, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "POST_NOTIFICATIONS permission denied", e);
        }
    }
}
