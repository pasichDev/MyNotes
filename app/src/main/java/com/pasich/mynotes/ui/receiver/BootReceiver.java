package com.pasich.mynotes.ui.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.utils.reminder.ReminderManager;
import com.pasich.mynotes.utils.reminder.TaskReminderManager;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Inject
    DataManager dataManager;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        dataManager.getNotesWithActiveReminders()
                .subscribe(
                        notes -> ReminderManager.rescheduleAll(ctx, notes),
                        e -> Log.e(TAG, "rescheduleAll failed", e)
                );

        dataManager.getTasksWithReminders()
                .subscribe(
                        tasks -> TaskReminderManager.rescheduleAll(ctx, tasks),
                        e -> Log.e(TAG, "rescheduleTasksAll failed", e)
                );
    }
}
