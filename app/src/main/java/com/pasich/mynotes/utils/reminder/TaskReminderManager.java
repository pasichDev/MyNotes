package com.pasich.mynotes.utils.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.ui.receiver.TaskReminderReceiver;

import java.util.List;

public class TaskReminderManager {

    public static final String EXTRA_TASK_ID = "taskId";
    public static final String EXTRA_TASK_TITLE = "taskTitle";
    public static final String EXTRA_TASK_INTERVAL_MINUTES = "intervalMinutes";

    // Offset to avoid collision with note reminder PendingIntent request codes
    private static final int REQUEST_CODE_OFFSET = 100000;

    public static void scheduleReminder(Context ctx, Task task) {
        if (task.getReminderTime() == null) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return;
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.getReminderTime(),
                buildPendingIntent(ctx, task));
    }

    public static void cancelReminder(Context ctx, int taskId) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(ctx, TaskReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, taskId + REQUEST_CODE_OFFSET, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        pi.cancel();
    }

    public static void rescheduleAll(Context ctx, List<Task> tasks) {
        for (Task t : tasks) scheduleReminder(ctx, t);
    }

    private static PendingIntent buildPendingIntent(Context ctx, Task task) {
        Intent intent = new Intent(ctx, TaskReminderReceiver.class);
        intent.putExtra(EXTRA_TASK_ID, task.getId());
        intent.putExtra(EXTRA_TASK_TITLE, task.getTitle());
        intent.putExtra(EXTRA_TASK_INTERVAL_MINUTES, task.getReminderIntervalMinutes());
        return PendingIntent.getBroadcast(ctx, task.getId() + REQUEST_CODE_OFFSET, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
