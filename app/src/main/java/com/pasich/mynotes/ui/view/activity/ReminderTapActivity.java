package com.pasich.mynotes.ui.view.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.utils.navigation.NoteExtras;
import com.pasich.mynotes.utils.navigation.NoteNavigator;
import com.pasich.mynotes.utils.reminder.ReminderManager;
import com.pasich.mynotes.utils.reminder.TaskReminderManager;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

/**
 * Trampoline activity for taps on a reminder notification that repeats until answered. Ends the
 * repeating notification, then navigates to the note or tasks screen.
 */
@AndroidEntryPoint
public class ReminderTapActivity extends AppCompatActivity {

    public static final String EXTRA_IS_TASK = "isTask";

    @Inject DataManager dataManager;

    @Inject ThemePreferencesCache themePreferencesCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent incoming = getIntent();
        boolean isTask = incoming.getBooleanExtra(EXTRA_IS_TASK, false);

        if (isTask) {
            int taskId = incoming.getIntExtra(TaskReminderManager.EXTRA_TASK_ID, -1);
            if (taskId == -1) {
                finish();
                return;
            }
            TaskReminderManager.cancelReminder(this, taskId);
            dataManager.clearTaskReminder(taskId).subscribe(() -> {}, e -> {});
            TaskStackBuilder.create(this)
                    .addNextIntentWithParentStack(new Intent(this, TasksActivity.class))
                    .startActivities();
        } else {
            int noteId = incoming.getIntExtra(ReminderManager.EXTRA_NOTE_ID, -1);
            if (noteId == -1) {
                finish();
                return;
            }
            // Tapping answers the notification: the "repeat notification" cycle ends. The
            // schedule already moved to its next occurrence when the reminder fired.
            ReminderManager.cancelNag(this, noteId);
            NotificationManagerCompat.from(this).cancel(noteId);

            Intent noteIntent =
                    NoteNavigator.existingNoteIntent(
                            this,
                            themePreferencesCache,
                            noteId,
                            incoming.getBooleanExtra(NoteExtras.EXTRA_HAS_ATTACHMENTS, false));
            TaskStackBuilder.create(this)
                    .addNextIntent(new Intent(this, MainActivity.class))
                    .addNextIntent(noteIntent)
                    .startActivities();
        }

        finish();
    }
}
