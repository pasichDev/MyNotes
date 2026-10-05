package com.pasich.mynotes.ui.receiver;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.pasich.mynotes.utils.reminder.ReminderRescheduler;
import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.schedulers.Schedulers;
import javax.inject.Inject;

/**
 * Re-arms every reminder when armed alarms were lost or can now be exact: after a reboot, after the
 * app was updated, and when "Alarms & reminders" is granted (the system then delivers this so
 * alarms armed inexactly meanwhile become exact).
 */
@AndroidEntryPoint
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Inject ReminderRescheduler reminderRescheduler;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(
                        action)) {
            return;
        }

        final PendingResult pendingResult = goAsync();
        Schedulers.io()
                .scheduleDirect(
                        () -> {
                            try {
                                reminderRescheduler.rescheduleAll(System.currentTimeMillis());
                            } catch (RuntimeException e) {
                                Log.e(TAG, "reschedule failed", e);
                            } finally {
                                pendingResult.finish();
                            }
                        });
    }
}
