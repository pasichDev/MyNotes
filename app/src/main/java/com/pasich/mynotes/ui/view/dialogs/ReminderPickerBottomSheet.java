package com.pasich.mynotes.ui.view.dialogs;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.ReminderRepeat;
import com.pasich.mynotes.utils.reminder.ReminderManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.disposables.CompositeDisposable;

@AndroidEntryPoint
public class ReminderPickerBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "ReminderPicker";
    private static final String ARG_NOTE_ID = "noteId";

    @Inject
    DataManager dataManager;

    private int noteId;
    private Long selectedTime = null;
    private ReminderRepeat selectedRepeat = ReminderRepeat.NONE;
    private Note currentNote;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private TextView selectedTimeDisplay;
    private TextView repeatLabel;
    private ChipGroup repeatChips;
    private MaterialButton btnSave;
    private MaterialButton btnDeleteReminder;

    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) saveReminder();
                else showPermissionDenied();
            });

    public static ReminderPickerBottomSheet newInstance(int noteId) {
        ReminderPickerBottomSheet f = new ReminderPickerBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_NOTE_ID, noteId);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        noteId = requireArguments().getInt(ARG_NOTE_ID, -1);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_reminder_picker, container, false);

        selectedTimeDisplay = view.findViewById(R.id.selectedTimeDisplay);
        repeatLabel         = view.findViewById(R.id.repeatLabel);
        repeatChips         = view.findViewById(R.id.repeatChips);
        btnSave             = view.findViewById(R.id.btnSave);
        btnDeleteReminder   = view.findViewById(R.id.btnDeleteReminder);

        disposables.add(
                dataManager.getNoteForId(noteId)
                        .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                        .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                        .subscribe(note -> {
                            currentNote = note;
                            prefillExistingReminder(note);
                        }, e -> Log.e(TAG, "load note failed", e))
        );

        view.findViewById(R.id.presetToday).setOnClickListener(v -> applyPreset(todayEvening()));
        view.findViewById(R.id.presetTomorrow).setOnClickListener(v -> applyPreset(tomorrowMorning()));
        view.findViewById(R.id.presetNextWeek).setOnClickListener(v -> applyPreset(nextWeekMorning()));
        view.findViewById(R.id.btnChooseDate).setOnClickListener(v -> showDatePicker());
        btnSave.setOnClickListener(v -> checkPermissionsAndSave());
        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());
        btnDeleteReminder.setOnClickListener(v -> deleteReminder());

        return view;
    }

    private void prefillExistingReminder(Note note) {
        if (note.hasReminder()) {
            selectedTime = note.getReminderTime();
            selectedRepeat = ReminderRepeat.from(note.getReminderRepeat());
            updateTimeDisplay();
            showRepeatSection();
            setRepeatChip(selectedRepeat);
            btnDeleteReminder.setVisibility(View.VISIBLE);
            btnSave.setEnabled(true);
        }
    }

    private long todayEvening() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 18);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    private long tomorrowMorning() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private long nextWeekMorning() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.WEEK_OF_YEAR, 1);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void applyPreset(long time) {
        selectedTime = time;
        updateTimeDisplay();
        showRepeatSection();
        btnSave.setEnabled(true);
    }

    private void showDatePicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build();

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder
                .datePicker()
                .setTitleText(getString(R.string.reminder_choose_date))
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .setCalendarConstraints(constraints)
                .build();

        datePicker.addOnPositiveButtonClickListener(dateMs -> {
            Calendar dateCal = Calendar.getInstance();
            dateCal.setTimeInMillis(dateMs);

            Calendar now = Calendar.getInstance();
            int defaultHour = (dateCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                    && dateCal.get(Calendar.YEAR) == now.get(Calendar.YEAR))
                    ? now.get(Calendar.HOUR_OF_DAY) : 9;
            int defaultMinute = (defaultHour == now.get(Calendar.HOUR_OF_DAY))
                    ? now.get(Calendar.MINUTE) + 1 : 0;

            MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(defaultHour)
                    .setMinute(defaultMinute)
                    .build();

            timePicker.addOnPositiveButtonClickListener(v -> {
                dateCal.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                dateCal.set(Calendar.MINUTE, timePicker.getMinute());
                dateCal.set(Calendar.SECOND, 0);
                dateCal.set(Calendar.MILLISECOND, 0);
                if (dateCal.getTimeInMillis() <= System.currentTimeMillis()) {
                    Toast.makeText(requireContext(),
                            R.string.reminder_past_time_error, Toast.LENGTH_SHORT).show();
                    return;
                }
                applyPreset(dateCal.getTimeInMillis());
            });

            timePicker.show(getChildFragmentManager(), "timePicker");
        });

        datePicker.show(getChildFragmentManager(), "datePicker");
    }

    private void showRepeatSection() {
        repeatLabel.setVisibility(View.VISIBLE);
        repeatChips.setVisibility(View.VISIBLE);
    }

    private void updateTimeDisplay() {
        if (selectedTime == null) return;
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault());
        selectedTimeDisplay.setText(fmt.format(new Date(selectedTime)));
        selectedTimeDisplay.setVisibility(View.VISIBLE);
    }

    private void setRepeatChip(ReminderRepeat repeat) {
        int chipId;
        switch (repeat) {
            case DAILY:   chipId = R.id.chipDaily; break;
            case WEEKLY:  chipId = R.id.chipWeekly; break;
            case MONTHLY: chipId = R.id.chipMonthly; break;
            default:      chipId = R.id.chipNone; break;
        }
        repeatChips.check(chipId);
    }

    private ReminderRepeat getSelectedRepeat() {
        int checkedId = repeatChips.getCheckedChipId();
        if (checkedId == R.id.chipDaily)   return ReminderRepeat.DAILY;
        if (checkedId == R.id.chipWeekly)  return ReminderRepeat.WEEKLY;
        if (checkedId == R.id.chipMonthly) return ReminderRepeat.MONTHLY;
        return ReminderRepeat.NONE;
    }

    private void checkPermissionsAndSave() {
        if (selectedTime == null || selectedTime <= System.currentTimeMillis()) {
            Toast.makeText(requireContext(),
                    R.string.reminder_past_time_error, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!ReminderManager.canScheduleExact(requireContext())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }

        saveReminder();
    }

    private void saveReminder() {
        if (selectedTime == null) return;

        selectedRepeat = getSelectedRepeat();
        final long time = selectedTime;
        final String repeat = selectedRepeat.name();

        disposables.add(
                dataManager.updateNoteReminder(noteId, time, repeat)
                        .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                        .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                            Note tempNote = new Note();
                            tempNote.setId(noteId);
                            if (currentNote != null) {
                                tempNote.setTitle(currentNote.getTitle());
                                tempNote.setValue(currentNote.getValue());
                            }
                            tempNote.setReminderTime(time);
                            tempNote.setReminderRepeat(repeat);
                            ReminderManager.scheduleReminder(requireContext(), tempNote);
                            dismiss();
                        }, e -> Log.e(TAG, "save failed", e))
        );
    }

    private void deleteReminder() {
        disposables.add(
                dataManager.clearReminder(noteId)
                        .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                        .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                            ReminderManager.cancelReminder(requireContext(), noteId);
                            dismiss();
                        }, e -> Log.e(TAG, "delete failed", e))
        );
    }

    private void showPermissionDenied() {
        Toast.makeText(requireContext(),
                R.string.reminder_permission_denied, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }
}
