package com.pasich.mynotes.ui.view.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.TaskStackBuilder;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.ReminderRepeat;
import com.pasich.mynotes.ui.view.activity.noteEditor.NoteActivity;
import com.pasich.mynotes.utils.navigation.NoteExtras;
import com.pasich.mynotes.utils.reminder.ReminderManager;
import com.pasich.mynotes.utils.reminder.TaskReminderManager;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

/**
 * Trampoline activity for interval reminder notification taps. Cancels the current interval alarm.
 * If the note has a daily/weekly/monthly repeat, schedules the next occurrence; otherwise clears
 * the reminder entirely. Then navigates to the note or tasks screen.
 */
@AndroidEntryPoint
public class ReminderTapActivity extends AppCompatActivity {

    public static final String EXTRA_IS_TASK = "isTask";

    @Inject DataManager dataManager;

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
            String repeatStr = incoming.getStringExtra(ReminderManager.EXTRA_NOTE_REPEAT);
            int intervalMinutes =
                    incoming.getIntExtra(ReminderManager.EXTRA_NOTE_INTERVAL_MINUTES, 0);
            String title = incoming.getStringExtra(ReminderManager.EXTRA_NOTE_TITLE);
            String preview = incoming.getStringExtra(ReminderManager.EXTRA_NOTE_PREVIEW);

            ReminderManager.cancelReminder(this, noteId);

            ReminderRepeat repeat = ReminderRepeat.from(repeatStr);
            if (repeat != ReminderRepeat.NONE) {
                long nextTime = ReminderManager.computeNextTime(System.currentTimeMillis(), repeat);
                Note tempNote = new Note();
                tempNote.setId(noteId);
                tempNote.setTitle(title != null ? title : "");
                tempNote.setValue(preview != null ? preview : "");
                tempNote.setReminderTime(nextTime);
                tempNote.setReminderRepeat(repeat.name());
                tempNote.setReminderIntervalMinutes(intervalMinutes);
                dataManager
                        .updateNoteReminderFull(noteId, nextTime, repeat.name(), intervalMinutes)
                        .subscribe(() -> {}, e -> {});
                ReminderManager.scheduleReminder(this, tempNote);
            } else {
                dataManager.clearReminder(noteId).subscribe(() -> {}, e -> {});
            }

            Intent noteIntent = new Intent(this, NoteActivity.class);
            noteIntent.putExtra(NoteExtras.EXTRA_NEW_NOTE, false);
            noteIntent.putExtra(NoteExtras.EXTRA_ID_NOTE, (long) noteId);
            TaskStackBuilder.create(this)
                    .addNextIntent(new Intent(this, MainActivity.class))
                    .addNextIntent(noteIntent)
                    .startActivities();
        }

        finish();
    }
}
