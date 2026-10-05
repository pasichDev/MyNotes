package com.pasich.mynotes.ui.receiver;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.NotificationPreferencesCache;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.RepeatRule;
import com.pasich.mynotes.ui.view.activity.MainActivity;
import com.pasich.mynotes.ui.view.activity.ReminderTapActivity;
import com.pasich.mynotes.ui.view.activity.SnoozeActivity;
import com.pasich.mynotes.utils.navigation.NoteExtras;
import com.pasich.mynotes.utils.navigation.NoteNavigator;
import com.pasich.mynotes.utils.reminder.ReminderManager;
import com.pasich.mynotes.utils.reminder.SnoozeStore;
import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.schedulers.Schedulers;
import javax.inject.Inject;

/**
 * Posts note reminder notifications and moves a repeating reminder to its next occurrence.
 *
 * <p>Each alarm is checked against the note as stored now, not as it was when the alarm was armed:
 * a note deleted, trashed or given another time meanwhile (here or by sync) is not announced by an
 * alarm that no longer describes it.
 */
@AndroidEntryPoint
public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderReceiver";

    /** An alarm arriving this much before the stored time is stale and is re-armed instead. */
    private static final long EARLY_TOLERANCE_MS = 60_000L;

    @Inject DataManager dataManager;

    @Inject NotificationPreferencesCache notificationPreferencesCache;

    @Inject ThemePreferencesCache themePreferencesCache;

    public static final String ACTION_DISMISS = "com.pasich.mynotes.ACTION_DISMISS_REMINDER";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int noteId = intent.getIntExtra(ReminderManager.EXTRA_NOTE_ID, -1);
        if (noteId == -1) return;
        Context app = ctx.getApplicationContext();

        if (ACTION_DISMISS.equals(intent.getAction())) {
            // Ends the "repeat notification" cycle only; the schedule moved on when it fired.
            ReminderManager.cancelNag(app, noteId);
            NotificationManagerCompat.from(app).cancel(noteId);
            return;
        }

        PendingResult pending = goAsync();
        Schedulers.io()
                .scheduleDirect(
                        () -> {
                            try {
                                handle(app, intent, noteId, System.currentTimeMillis());
                            } catch (RuntimeException e) {
                                Log.e(TAG, "reminder handling failed", e);
                            } finally {
                                pending.finish();
                            }
                        });
    }

    @WorkerThread
    private void handle(Context ctx, Intent intent, int noteId, long now) {
        String action = intent.getAction();
        int alarmNag = intent.getIntExtra(ReminderManager.EXTRA_NOTE_INTERVAL_MINUTES, 0);
        Note note = loadNote(noteId);

        if (ReminderManager.ACTION_NAG.equals(action)) {
            if (note == null || note.isTrash()) return;
            showNotification(ctx, note, alarmNag);
            ReminderManager.scheduleNag(ctx, noteId, alarmNag, now);
            return;
        }

        if (ReminderManager.ACTION_SNOOZE.equals(action)) {
            SnoozeStore snoozes = new SnoozeStore(ctx);
            SnoozeStore.Entry entry = snoozes.get(noteId);
            long scheduledAt = intent.getLongExtra(ReminderManager.EXTRA_SCHEDULED_AT, -1L);
            // Re-armed by the rescheduler after it already fired, or replaced by a later snooze.
            if (entry == null || entry.time != scheduledAt) return;
            snoozes.remove(noteId);
            if (note == null || note.isTrash()) return;
            showNotification(ctx, note, entry.intervalMinutes);
            if (entry.intervalMinutes > 0) {
                ReminderManager.scheduleNag(ctx, noteId, entry.intervalMinutes, now);
            }
            return;
        }

        if (note == null || note.isTrash() || note.getReminderTime() == null) {
            ReminderManager.cancelNag(ctx, noteId);
            return;
        }
        long due = note.getReminderTime();
        if (due > now + EARLY_TOLERANCE_MS) {
            // Armed for a time the note no longer has, typically moved by sync; wait for it.
            ReminderManager.scheduleReminder(ctx, note);
            return;
        }

        int nag = note.getReminderIntervalMinutes();
        showNotification(ctx, note, nag);

        RepeatRule rule = RepeatRule.parse(note.getReminderRepeat());
        if (rule.isRepeating()) {
            // Counted from the anchor, so neither a late delivery nor days of downtime shift it,
            // and missed periods are skipped rather than announced one after another.
            long next = rule.next(rule.anchorOr(due), Math.max(now, due));
            Log.d(TAG, "repeat " + note.getReminderRepeat() + " → next at " + next);
            note.setReminderTime(next);
            try {
                dataManager
                        .updateNoteReminderFull(noteId, next, note.getReminderRepeat(), nag)
                        .blockingAwait();
            } catch (RuntimeException e) {
                Log.e(TAG, "advancing the reminder failed", e);
            }
            ReminderManager.scheduleReminder(ctx, note);
        } else {
            try {
                dataManager.clearReminder(noteId).blockingAwait();
            } catch (RuntimeException e) {
                Log.e(TAG, "clearReminder failed", e);
            }
        }

        if (nag > 0) ReminderManager.scheduleNag(ctx, noteId, nag, now);
        else ReminderManager.cancelNag(ctx, noteId);
    }

    @Nullable
    private Note loadNote(int noteId) {
        try {
            Note note = dataManager.getNoteForId(noteId).blockingGet();
            // A missing note comes back as an empty placeholder.
            return note != null && note.getId() == noteId ? note : null;
        } catch (RuntimeException e) {
            Log.w(TAG, "note lookup failed", e);
            return null;
        }
    }

    private void showNotification(@NonNull Context ctx, @NonNull Note note, int intervalMinutes) {
        int noteId = note.getId();
        String title = note.getTitle();
        String preview = note.getValuePreview();
        boolean hasAttachments = note.isAttachments();
        Intent noteIntent =
                NoteNavigator.existingNoteIntent(
                        ctx, themePreferencesCache, noteId, hasAttachments);

        PendingIntent openPi =
                TaskStackBuilder.create(ctx)
                        .addNextIntent(new Intent(ctx, MainActivity.class))
                        .addNextIntent(noteIntent)
                        .getPendingIntent(
                                noteId,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent tapIntent = new Intent(ctx, ReminderTapActivity.class);
        tapIntent.putExtra(ReminderManager.EXTRA_NOTE_ID, noteId);
        tapIntent.putExtra(NoteExtras.EXTRA_HAS_ATTACHMENTS, hasAttachments);
        PendingIntent tapPi =
                PendingIntent.getActivity(
                        ctx,
                        noteId + 20000,
                        tapIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent snoozeIntent = new Intent(ctx, SnoozeActivity.class);
        snoozeIntent.putExtra(ReminderManager.EXTRA_NOTE_ID, noteId);
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

        // With "repeat notification" on, tapping opens the note and also ends the cycle.
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
