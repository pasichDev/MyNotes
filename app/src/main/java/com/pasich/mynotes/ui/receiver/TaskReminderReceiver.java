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
import com.pasich.mynotes.ui.view.activity.TasksActivity;
import com.pasich.mynotes.utils.reminder.TaskReminderManager;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TaskReminderReceiver extends BroadcastReceiver {

    @Inject
    DataManager dataManager;

    @Inject
    NotificationPreferencesCache notificationPreferencesCache;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int taskId = intent.getIntExtra(TaskReminderManager.EXTRA_TASK_ID, -1);
        String title = intent.getStringExtra(TaskReminderManager.EXTRA_TASK_TITLE);
        int intervalMinutes = intent.getIntExtra(TaskReminderManager.EXTRA_TASK_INTERVAL_MINUTES, 0);
        if (taskId == -1) return;

        Intent openIntent = new Intent(ctx, TasksActivity.class);
        PendingIntent contentIntent = TaskStackBuilder.create(ctx)
                .addNextIntentWithParentStack(openIntent)
                .getPendingIntent(taskId + 200000,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                ctx, notificationPreferencesCache.getChannelId())
                .setSmallIcon(R.drawable.ic_bell_small)
                .setContentTitle(ctx.getString(R.string.app_name))
                .setContentText(title)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent);

        try {
            NotificationManagerCompat.from(ctx).notify(taskId + 100000, builder.build());
        } catch (SecurityException ignored) {}

        if (intervalMinutes > 0) {
            long nextTime = System.currentTimeMillis() + intervalMinutes * 60_000L;
            dataManager.setTaskReminderFull(taskId, nextTime, intervalMinutes)
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

        dataManager.clearTaskReminder(taskId)
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .subscribe(() -> {}, e -> {});
    }
}
