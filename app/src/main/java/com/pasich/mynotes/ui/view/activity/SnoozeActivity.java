package com.pasich.mynotes.ui.view.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.ReminderRepeat;
import com.pasich.mynotes.utils.reminder.ReminderManager;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.Calendar;

/** Activity for choosing a snooze duration for a reminder. */
@AndroidEntryPoint
public class SnoozeActivity extends AppCompatActivity {

    private int noteId;
    private String noteTitle;
    private String notePreview;
    private String noteRepeat;
    private int noteIntervalMinutes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        noteId = intent.getIntExtra(ReminderManager.EXTRA_NOTE_ID, -1);
        noteTitle = intent.getStringExtra(ReminderManager.EXTRA_NOTE_TITLE);
        notePreview = intent.getStringExtra(ReminderManager.EXTRA_NOTE_PREVIEW);
        noteRepeat = intent.getStringExtra(ReminderManager.EXTRA_NOTE_REPEAT);
        noteIntervalMinutes = intent.getIntExtra(ReminderManager.EXTRA_NOTE_INTERVAL_MINUTES, 0);

        if (noteId == -1) {
            finish();
            return;
        }

        NotificationManagerCompat.from(this).cancel(noteId);
        showSnoozeSheet();
    }

    private void showSnoozeSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(com.pasich.mynotes.R.layout.dialog_snooze, null);
        dialog.setContentView(view);
        dialog.setOnDismissListener(d -> finish());

        view.findViewById(com.pasich.mynotes.R.id.snooze10min)
                .setOnClickListener(
                        v -> {
                            snooze(10 * 60 * 1000L);
                            dialog.dismiss();
                        });
        view.findViewById(com.pasich.mynotes.R.id.snooze1hour)
                .setOnClickListener(
                        v -> {
                            snooze(60 * 60 * 1000L);
                            dialog.dismiss();
                        });
        view.findViewById(com.pasich.mynotes.R.id.snoozeTomorrow)
                .setOnClickListener(
                        v -> {
                            snoozeTomorrow();
                            dialog.dismiss();
                        });

        dialog.show();
    }

    private void snooze(long delayMs) {
        scheduleSnooze(System.currentTimeMillis() + delayMs);
    }

    private void snoozeTomorrow() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        scheduleSnooze(cal.getTimeInMillis());
    }

    private void scheduleSnooze(long time) {
        Note tempNote = new Note();
        tempNote.setId(noteId);
        tempNote.setTitle(noteTitle != null ? noteTitle : "");
        tempNote.setValue(notePreview != null ? notePreview : "");
        tempNote.setReminderTime(time);
        tempNote.setReminderRepeat(noteRepeat != null ? noteRepeat : ReminderRepeat.NONE.name());
        tempNote.setReminderIntervalMinutes(noteIntervalMinutes);
        ReminderManager.scheduleReminder(this, tempNote);
    }
}
