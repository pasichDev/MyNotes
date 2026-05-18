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
import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.disposables.CompositeDisposable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import javax.inject.Inject;

@AndroidEntryPoint
public class ReminderPickerBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "ReminderPicker";
    private static final String ARG_NOTE_ID = "noteId";

    @Inject DataManager dataManager;

    private int noteId;
    private Long selectedTime = null;
    private ReminderRepeat selectedRepeat = ReminderRepeat.NONE;
    private Note currentNote;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private View selectedTimeCard;
    private TextView selectedTimeDisplay;
    private TextView repeatLabel;
    private ChipGroup repeatChips;
    private MaterialButton btnSave;
    private MaterialButton btnDeleteReminder;
    private int selectedIntervalMinutes = 0;
    private View intervalDivider;
    private com.google.android.material.materialswitch.MaterialSwitch switchRepeatInterval;
    private ChipGroup intervalChips;

    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
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
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_reminder_picker, container, false);

        selectedTimeCard = view.findViewById(R.id.selectedTimeCard);
        selectedTimeDisplay = view.findViewById(R.id.selectedTimeDisplay);
        repeatLabel = view.findViewById(R.id.repeatLabel);
        repeatChips = view.findViewById(R.id.repeatChips);
        btnSave = view.findViewById(R.id.btnSave);
        btnDeleteReminder = view.findViewById(R.id.btnDeleteReminder);
        intervalDivider = view.findViewById(R.id.intervalDivider);
        switchRepeatInterval = view.findViewById(R.id.switchRepeatInterval);
        intervalChips = view.findViewById(R.id.intervalChips);

        switchRepeatInterval.setOnCheckedChangeListener(
                (btn, checked) -> {
                    intervalChips.setVisibility(checked ? View.VISIBLE : View.GONE);
                    if (!checked) {
                        selectedIntervalMinutes = 0;
                    } else {
                        intervalChips.check(R.id.chipInterval10);
                        selectedIntervalMinutes = 10;
                    }
                });

        intervalChips.setOnCheckedStateChangeListener(
                (group, checkedIds) -> {
                    if (!checkedIds.isEmpty()) {
                        selectedIntervalMinutes = intervalMinutesFromChipId(checkedIds.get(0));
                    }
                });

        disposables.add(
                dataManager
                        .getNoteForId(noteId)
                        .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                        .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                        .subscribe(
                                note -> {
                                    currentNote = note;
                                    prefillExistingReminder(note);
                                },
                                e -> Log.e(TAG, "load note failed", e)));

        view.findViewById(R.id.presetToday).setOnClickListener(v -> applyPreset(todayEvening()));
        view.findViewById(R.id.presetTomorrow)
                .setOnClickListener(v -> applyPreset(tomorrowMorning()));
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
            int existingInterval = note.getReminderIntervalMinutes();
            if (existingInterval > 0) {
                selectedIntervalMinutes = existingInterval;
                switchRepeatInterval.setChecked(true);
                intervalChips.setVisibility(View.VISIBLE);
                setIntervalChip(existingInterval);
            }
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
        CalendarConstraints constraints =
                new CalendarConstraints.Builder()
                        .setValidator(DateValidatorPointForward.now())
                        .build();

        MaterialDatePicker<Long> datePicker =
                MaterialDatePicker.Builder.datePicker()
                        .setTitleText(getString(R.string.reminder_choose_date))
                        .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                        .setCalendarConstraints(constraints)
                        .build();

        datePicker.addOnPositiveButtonClickListener(
                dateMs -> {
                    Calendar dateCal = Calendar.getInstance();
                    dateCal.setTimeInMillis(dateMs);

                    Calendar now = Calendar.getInstance();
                    int defaultHour =
                            (dateCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                                            && dateCal.get(Calendar.YEAR) == now.get(Calendar.YEAR))
                                    ? now.get(Calendar.HOUR_OF_DAY)
                                    : 9;
                    int defaultMinute =
                            (defaultHour == now.get(Calendar.HOUR_OF_DAY))
                                    ? now.get(Calendar.MINUTE) + 1
                                    : 0;

                    MaterialTimePicker timePicker =
                            new MaterialTimePicker.Builder()
                                    .setTimeFormat(TimeFormat.CLOCK_24H)
                                    .setHour(defaultHour)
                                    .setMinute(defaultMinute)
                                    .build();

                    timePicker.addOnPositiveButtonClickListener(
                            v -> {
                                dateCal.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                                dateCal.set(Calendar.MINUTE, timePicker.getMinute());
                                dateCal.set(Calendar.SECOND, 0);
                                dateCal.set(Calendar.MILLISECOND, 0);
                                if (dateCal.getTimeInMillis() <= System.currentTimeMillis()) {
                                    Toast.makeText(
                                                    requireContext(),
                                                    R.string.reminder_past_time_error,
                                                    Toast.LENGTH_SHORT)
                                            .show();
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
        intervalDivider.setVisibility(View.VISIBLE);
        switchRepeatInterval.setVisibility(View.VISIBLE);
    }

    private void updateTimeDisplay() {
        if (selectedTime == null) return;
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault());
        selectedTimeDisplay.setText(fmt.format(new Date(selectedTime)));
        selectedTimeCard.setVisibility(View.VISIBLE);
    }

    private void setRepeatChip(ReminderRepeat repeat) {
        int chipId =
                switch (repeat) {
                    case DAILY -> R.id.chipDaily;
                    case WEEKLY -> R.id.chipWeekly;
                    case MONTHLY -> R.id.chipMonthly;
                    default -> R.id.chipNone;
                };
        repeatChips.check(chipId);
    }

    private ReminderRepeat getSelectedRepeat() {
        int checkedId = repeatChips.getCheckedChipId();
        if (checkedId == R.id.chipDaily) return ReminderRepeat.DAILY;
        if (checkedId == R.id.chipWeekly) return ReminderRepeat.WEEKLY;
        if (checkedId == R.id.chipMonthly) return ReminderRepeat.MONTHLY;
        return ReminderRepeat.NONE;
    }

    private int intervalMinutesFromChipId(int chipId) {
        if (chipId == R.id.chipInterval5) return 5;
        if (chipId == R.id.chipInterval10) return 10;
        if (chipId == R.id.chipInterval15) return 15;
        if (chipId == R.id.chipInterval30) return 30;
        if (chipId == R.id.chipInterval60) return 60;
        return 0;
    }

    private void setIntervalChip(int minutes) {
        int id;
        if (minutes <= 5) id = R.id.chipInterval5;
        else if (minutes <= 10) id = R.id.chipInterval10;
        else if (minutes <= 15) id = R.id.chipInterval15;
        else if (minutes <= 30) id = R.id.chipInterval30;
        else id = R.id.chipInterval60;
        intervalChips.check(id);
    }

    private void checkPermissionsAndSave() {
        if (selectedTime == null || selectedTime <= System.currentTimeMillis()) {
            Toast.makeText(requireContext(), R.string.reminder_past_time_error, Toast.LENGTH_SHORT)
                    .show();
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
            if (ActivityCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
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
                dataManager
                        .updateNoteReminderFull(noteId, time, repeat, selectedIntervalMinutes)
                        .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                        .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> {
                                    Note tempNote = new Note();
                                    tempNote.setId(noteId);
                                    if (currentNote != null) {
                                        tempNote.setTitle(currentNote.getTitle());
                                        tempNote.setValue(currentNote.getValue());
                                    }
                                    tempNote.setReminderTime(time);
                                    tempNote.setReminderRepeat(repeat);
                                    tempNote.setReminderIntervalMinutes(selectedIntervalMinutes);
                                    ReminderManager.scheduleReminder(requireContext(), tempNote);

                                    Bundle result = new Bundle();
                                    result.putBoolean("hasReminder", true);
                                    result.putLong("reminderTime", time);
                                    getParentFragmentManager()
                                            .setFragmentResult("reminderChanged", result);

                                    dismiss();
                                },
                                e -> Log.e(TAG, "save failed", e)));
    }

    private void deleteReminder() {
        disposables.add(
                dataManager
                        .clearReminder(noteId)
                        .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                        .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> {
                                    ReminderManager.cancelReminder(requireContext(), noteId);

                                    Bundle result = new Bundle();
                                    result.putBoolean("hasReminder", false);
                                    getParentFragmentManager()
                                            .setFragmentResult("reminderChanged", result);

                                    dismiss();
                                },
                                e -> Log.e(TAG, "delete failed", e)));
    }

    private void showPermissionDenied() {
        Toast.makeText(requireContext(), R.string.reminder_permission_denied, Toast.LENGTH_SHORT)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }
}
