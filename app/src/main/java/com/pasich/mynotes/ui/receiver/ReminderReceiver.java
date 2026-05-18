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
import com.pasich.mynotes.ui.view.activity.ReminderTapActivity;
import com.pasich.mynotes.ui.view.activity.SnoozeActivity;
import com.pasich.mynotes.ui.view.activity.noteEditor.NoteActivity;
import com.pasich.mynotes.utils.navigation.NoteExtras;
import com.pasich.mynotes.utils.reminder.ReminderManager;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderReceiver";

    @Inject DataManager dataManager;

    @Inject NotificationPreferencesCache notificationPreferencesCache;

    public static final String ACTION_DISMISS = "com.pasich.mynotes.ACTION_DISMISS_REMINDER";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int noteId = intent.getIntExtra(ReminderManager.EXTRA_NOTE_ID, -1);
        if (noteId == -1) return;

        if (ACTION_DISMISS.equals(intent.getAction())) {
            String dRepeat = intent.getStringExtra(ReminderManager.EXTRA_NOTE_REPEAT);
            int dInterval = intent.getIntExtra(ReminderManager.EXTRA_NOTE_INTERVAL_MINUTES, 0);
            String dTitle = intent.getStringExtra(ReminderManager.EXTRA_NOTE_TITLE);
            String dPreview = intent.getStringExtra(ReminderManager.EXTRA_NOTE_PREVIEW);
            cancelIntervalReminder(ctx, noteId, dRepeat, dInterval, dTitle, dPreview);
            return;
        }

        String title = intent.getStringExtra(ReminderManager.EXTRA_NOTE_TITLE);
        String preview = intent.getStringExtra(ReminderManager.EXTRA_NOTE_PREVIEW);
        String repeatStr = intent.getStringExtra(ReminderManager.EXTRA_NOTE_REPEAT);
        int intervalMinutes = intent.getIntExtra(ReminderManager.EXTRA_NOTE_INTERVAL_MINUTES, 0);

        Log.d(
                TAG,
                "onReceive: noteId="
                        + noteId
                        + " repeatStr="
                        + repeatStr
                        + " intervalMinutes="
                        + intervalMinutes);

        showNotification(ctx, noteId, title, preview, repeatStr, intervalMinutes);

        if (intervalMinutes > 0) {
            long nextTime = System.currentTimeMillis() + intervalMinutes * 60_000L;
            Log.d(TAG, "onReceive: INTERVAL path → nextTime=" + new java.util.Date(nextTime));
            dataManager
                    .updateNoteReminderFull(
                            noteId,
                            nextTime,
                            repeatStr != null ? repeatStr : "NONE",
                            intervalMinutes)
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
            Log.d(TAG, "onReceive: ONE-TIME path → clearing reminder for noteId=" + noteId);
            dataManager
                    .clearReminder(noteId)
                    .subscribe(() -> {}, e -> Log.e(TAG, "clearReminder failed", e));
        } else {
            long nextTime = ReminderManager.computeNextTime(System.currentTimeMillis(), repeat);
            Log.d(
                    TAG,
                    "onReceive: REPEAT("
                            + repeat
                            + ") path → scheduling next at "
                            + new java.util.Date(nextTime));
            dataManager
                    .updateNoteReminderFull(noteId, nextTime, repeat.name(), 0)
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

    private void cancelIntervalReminder(
            Context ctx,
            int noteId,
            String repeatStr,
            int intervalMinutes,
            String title,
            String preview) {
        ReminderManager.cancelReminder(ctx, noteId);
        NotificationManagerCompat.from(ctx).cancel(noteId);

        ReminderRepeat repeat = ReminderRepeat.from(repeatStr);
        if (repeat != ReminderRepeat.NONE) {
            long nextTime = ReminderManager.computeNextTime(System.currentTimeMillis(), repeat);
            dataManager
                    .updateNoteReminderFull(noteId, nextTime, repeat.name(), intervalMinutes)
                    .subscribe(() -> {}, e -> Log.e(TAG, "updateReminderFull failed", e));
            Note tempNote = new Note();
            tempNote.setId(noteId);
            tempNote.setTitle(title != null ? title : "");
            tempNote.setValue(preview != null ? preview : "");
            tempNote.setReminderTime(nextTime);
            tempNote.setReminderRepeat(repeat.name());
            tempNote.setReminderIntervalMinutes(intervalMinutes);
            ReminderManager.scheduleReminder(ctx, tempNote);
        } else {
            dataManager
                    .clearReminder(noteId)
                    .subscribe(() -> {}, e -> Log.e(TAG, "clearReminder failed", e));
        }
    }

    private void showNotification(
            Context ctx,
            int noteId,
            String title,
            String preview,
            String repeatStr,
            int intervalMinutes) {
        Intent noteIntent = new Intent(ctx, NoteActivity.class);
        noteIntent.putExtra(NoteExtras.EXTRA_NEW_NOTE, false);
        noteIntent.putExtra(NoteExtras.EXTRA_ID_NOTE, (long) noteId);

        PendingIntent openPi =
                TaskStackBuilder.create(ctx)
                        .addNextIntent(new Intent(ctx, MainActivity.class))
                        .addNextIntent(noteIntent)
                        .getPendingIntent(
                                noteId,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent tapIntent = new Intent(ctx, ReminderTapActivity.class);
        tapIntent.putExtra(ReminderManager.EXTRA_NOTE_ID, noteId);
        tapIntent.putExtra(ReminderManager.EXTRA_NOTE_TITLE, title);
        tapIntent.putExtra(ReminderManager.EXTRA_NOTE_PREVIEW, preview);
        tapIntent.putExtra(ReminderManager.EXTRA_NOTE_REPEAT, repeatStr);
        tapIntent.putExtra(ReminderManager.EXTRA_NOTE_INTERVAL_MINUTES, intervalMinutes);
        PendingIntent tapPi =
                PendingIntent.getActivity(
                        ctx,
                        noteId + 20000,
                        tapIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent snoozeIntent = new Intent(ctx, SnoozeActivity.class);
        snoozeIntent.putExtra(ReminderManager.EXTRA_NOTE_ID, noteId);
        snoozeIntent.putExtra(ReminderManager.EXTRA_NOTE_TITLE, title);
        snoozeIntent.putExtra(ReminderManager.EXTRA_NOTE_PREVIEW, preview);
        snoozeIntent.putExtra(ReminderManager.EXTRA_NOTE_REPEAT, repeatStr);
        snoozeIntent.putExtra(ReminderManager.EXTRA_NOTE_INTERVAL_MINUTES, intervalMinutes);
        snoozeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent snoozePi =
                PendingIntent.getActivity(
                        ctx,
                        noteId + 10000,
                        snoozeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent dismissIntent = new Intent(ctx, ReminderReceiver.class);
        dismissIntent.setAction(ACTION_DISMISS);
        dismissIntent.putExtra(ReminderManager.EXTRA_NOTE_ID, noteId);
        dismissIntent.putExtra(ReminderManager.EXTRA_NOTE_TITLE, title);
        dismissIntent.putExtra(ReminderManager.EXTRA_NOTE_PREVIEW, preview);
        dismissIntent.putExtra(ReminderManager.EXTRA_NOTE_REPEAT, repeatStr);
        dismissIntent.putExtra(ReminderManager.EXTRA_NOTE_INTERVAL_MINUTES, intervalMinutes);
        PendingIntent dismissPi =
                PendingIntent.getBroadcast(
                        ctx,
                        noteId + 30000,
                        dismissIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String notifTitle =
                (title != null && !title.isEmpty()) ? title : ctx.getString(R.string.app_name);
        String notifText =
                (preview != null && preview.length() > 100)
                        ? preview.substring(0, 100)
                        : (preview != null ? preview : "");

        // For interval reminders, tapping opens the note and stops the current interval cycle
        // (scheduling next daily/weekly/monthly occurrence if repeatStr != NONE).
        // For one-time / periodic reminders, tapping just opens the note.
        PendingIntent contentPi = (intervalMinutes > 0) ? tapPi : openPi;

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(ctx, notificationPreferencesCache.getChannelId())
                        .setSmallIcon(R.drawable.ic_bell_small)
                        .setContentTitle(notifTitle)
                        .setContentText(notifText)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(contentPi)
                        .addAction(0, ctx.getString(R.string.reminder_snooze_label), snoozePi);

        if (intervalMinutes > 0) {
            builder.addAction(0, ctx.getString(R.string.reminder_dismiss_label), dismissPi);
        }

        NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
        try {
            nm.notify(noteId, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "POST_NOTIFICATIONS permission denied", e);
        }
    }
}
