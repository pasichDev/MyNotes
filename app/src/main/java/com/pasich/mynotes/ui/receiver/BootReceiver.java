package com.pasich.mynotes.ui.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.utils.reminder.ReminderManager;
import com.pasich.mynotes.utils.reminder.TaskReminderManager;
import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.Single;
import javax.inject.Inject;

/** BroadcastReceiver that reschedules all reminders after device reboot. */
@AndroidEntryPoint
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Inject DataManager dataManager;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        final PendingResult pendingResult = goAsync();

        Single.zip(
                        dataManager.getNotesWithActiveReminders(),
                        dataManager.getTasksWithReminders(),
                        (notes, tasks) -> {
                            ReminderManager.rescheduleAll(ctx, notes);
                            TaskReminderManager.rescheduleAll(ctx, tasks);
                            return true;
                        })
                .subscribe(
                        result -> pendingResult.finish(),
                        e -> {
                            Log.e(TAG, "reschedule failed", e);
                            pendingResult.finish();
                        });
    }
}
