package com.pasich.mynotes.ui.receiver;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import com.pasich.mynotes.R;
import com.pasich.mynotes.cache.NotificationPreferencesCache;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.ui.view.activity.ReminderTapActivity;
import com.pasich.mynotes.ui.view.activity.TasksActivity;
import com.pasich.mynotes.utils.reminder.TaskReminderManager;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class TaskReminderReceiver extends BroadcastReceiver {

    private static final int NOTIF_ID_OFFSET = 100000;

    public static final String ACTION_DISMISS_TASK =
            "com.pasich.mynotes.ACTION_DISMISS_TASK_REMINDER";

    @Inject DataManager dataManager;

    @Inject NotificationPreferencesCache notificationPreferencesCache;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int taskId = intent.getIntExtra(TaskReminderManager.EXTRA_TASK_ID, -1);
        if (taskId == -1) return;

        if (ACTION_DISMISS_TASK.equals(intent.getAction())) {
            cancelIntervalReminder(ctx, taskId);
            return;
        }

        String title = intent.getStringExtra(TaskReminderManager.EXTRA_TASK_TITLE);
        int intervalMinutes =
                intent.getIntExtra(TaskReminderManager.EXTRA_TASK_INTERVAL_MINUTES, 0);

        showNotification(ctx, taskId, title, intervalMinutes);

        if (intervalMinutes > 0) {
            long nextTime = System.currentTimeMillis() + intervalMinutes * 60_000L;
            dataManager
                    .setTaskReminderFull(taskId, nextTime, intervalMinutes)
                    .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                    .subscribe(() -> {}, e -> {});
            Task tempTask = new Task();
            tempTask.setId(taskId);
            tempTask.setTitle(title != null ? title : "");
            tempTask.setReminderTime(nextTime);
            tempTask.setReminderIntervalMinutes(intervalMinutes);
            TaskReminderManager.scheduleReminder(ctx, tempTask);
            return;
        }

        dataManager
                .clearTaskReminder(taskId)
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .subscribe(() -> {}, e -> {});
    }

    private void cancelIntervalReminder(Context ctx, int taskId) {
        TaskReminderManager.cancelReminder(ctx, taskId);
        dataManager
                .clearTaskReminder(taskId)
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .subscribe(() -> {}, e -> {});
        NotificationManagerCompat.from(ctx).cancel(taskId + NOTIF_ID_OFFSET);
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
