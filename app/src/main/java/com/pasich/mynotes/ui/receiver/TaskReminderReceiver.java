package com.pasich.mynotes.ui.receiver;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.NotificationPreferencesCache;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.database.AppDatabase;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.ui.view.activity.ReminderTapActivity;
import com.pasich.mynotes.ui.view.activity.TasksActivity;
import com.pasich.mynotes.utils.reminder.TaskReminderManager;
import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.schedulers.Schedulers;
import javax.inject.Inject;

/**
 * Posts task reminder notifications and re-arms a reminder that repeats every N minutes.
 *
 * <p>Each alarm is checked against the task as stored now, not as it was when the alarm was armed:
 * a task deleted, completed or given another time meanwhile (here or by sync) is not announced. The
 * database is written before the broadcast finishes, so the process being killed right after cannot
 * leave a reminder that already fired stored as pending.
 */
@AndroidEntryPoint
public class TaskReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "TaskReminderReceiver";

    private static final int NOTIF_ID_OFFSET = 100000;

    /** An alarm arriving this much before the stored time is stale and is re-armed instead. */
    private static final long EARLY_TOLERANCE_MS = 60_000L;

    public static final String ACTION_DISMISS_TASK =
            "com.pasich.mynotes.ACTION_DISMISS_TASK_REMINDER";

    @Inject DataManager dataManager;

    @Inject AppDatabase database;

    @Inject NotificationPreferencesCache notificationPreferencesCache;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int taskId = intent.getIntExtra(TaskReminderManager.EXTRA_TASK_ID, -1);
        if (taskId == -1) return;
        Context app = ctx.getApplicationContext();
        boolean dismiss = ACTION_DISMISS_TASK.equals(intent.getAction());

        PendingResult pending = goAsync();
        Schedulers.io()
                .scheduleDirect(() -> {
                    try {
                        if (dismiss) cancelIntervalReminder(app, taskId);
                        else handle(app, taskId, System.currentTimeMillis());
                    } catch (RuntimeException e) {
                        Log.e(TAG, "task reminder handling failed", e);
                    } finally {
                        pending.finish();
                    }
                });
    }

    @WorkerThread
    @VisibleForTesting
    void handle(Context ctx, int taskId, long now) {
        Task task = loadTask(taskId);
        if (task == null || task.isDone() || task.getReminderTime() == null) {
            // Deleted, completed or turned off since the alarm was armed.
            TaskReminderManager.cancelReminder(ctx, taskId);
            return;
        }
        long due = task.getReminderTime();
        if (due > now + EARLY_TOLERANCE_MS) {
            // Armed for a time the task no longer has, typically moved by sync; wait for it.
            TaskReminderManager.scheduleReminder(ctx, task);
            return;
        }

        int intervalMinutes = task.getReminderIntervalMinutes();
        showNotification(ctx, taskId, task.getTitle(), intervalMinutes);

        if (intervalMinutes > 0) {
            long nextTime = now + intervalMinutes * 60_000L;
            try {
                dataManager.setTaskReminderFull(taskId, nextTime, intervalMinutes).blockingAwait();
            } catch (RuntimeException e) {
                Log.e(TAG, "advancing the task reminder failed", e);
            }
            task.setReminderTime(nextTime);
            TaskReminderManager.scheduleReminder(ctx, task);
            return;
        }

        try {
            dataManager.clearTaskReminder(taskId).blockingAwait();
        } catch (RuntimeException e) {
            Log.e(TAG, "clearTaskReminder failed", e);
        }
    }

    @Nullable
    private Task loadTask(int taskId) {
        try {
            return database.taskDao().getTaskSync(taskId);
        } catch (RuntimeException e) {
            Log.w(TAG, "task lookup failed", e);
            return null;
        }
    }

    @WorkerThread
    private void cancelIntervalReminder(Context ctx, int taskId) {
        TaskReminderManager.cancelReminder(ctx, taskId);
        NotificationManagerCompat.from(ctx).cancel(taskId + NOTIF_ID_OFFSET);
        try {
            dataManager.clearTaskReminder(taskId).blockingAwait();
        } catch (RuntimeException e) {
            Log.e(TAG, "clearTaskReminder failed", e);
        }
    }

    private void showNotification(Context ctx, int taskId, String title, int intervalMinutes) {
        Intent openIntent = new Intent(ctx, TasksActivity.class);
        PendingIntent openPi =
                TaskStackBuilder.create(ctx)
                        .addNextIntentWithParentStack(openIntent)
                        .getPendingIntent(
                                taskId + NOTIF_ID_OFFSET,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent tapIntent = new Intent(ctx, ReminderTapActivity.class);
        tapIntent.putExtra(ReminderTapActivity.EXTRA_IS_TASK, true);
        tapIntent.putExtra(TaskReminderManager.EXTRA_TASK_ID, taskId);
        PendingIntent tapPi =
                PendingIntent.getActivity(
                        ctx,
                        taskId + NOTIF_ID_OFFSET + 20000,
                        tapIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent dismissIntent = new Intent(ctx, TaskReminderReceiver.class);
        dismissIntent.setAction(ACTION_DISMISS_TASK);
        dismissIntent.putExtra(TaskReminderManager.EXTRA_TASK_ID, taskId);
        PendingIntent dismissPi =
                PendingIntent.getBroadcast(
                        ctx,
                        taskId + NOTIF_ID_OFFSET + 30000,
                        dismissIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // For interval reminders, tapping opens tasks AND cancels the repeat.
        PendingIntent contentPi = (intervalMinutes > 0) ? tapPi : openPi;

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(ctx, notificationPreferencesCache.getChannelId())
                        .setSmallIcon(R.drawable.ic_bell_small)
                        .setContentTitle(ctx.getString(R.string.app_name))
                        .setContentText(title)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(contentPi);

        if (intervalMinutes > 0) {
            builder.addAction(0, ctx.getString(R.string.reminder_dismiss_label), dismissPi);
        }

        try {
            NotificationManagerCompat.from(ctx).notify(taskId + NOTIF_ID_OFFSET, builder.build());
        } catch (SecurityException ignored) {
        }
    }
}
