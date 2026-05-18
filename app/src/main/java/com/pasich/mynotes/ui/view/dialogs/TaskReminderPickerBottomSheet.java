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
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.utils.reminder.ReminderManager;
import com.pasich.mynotes.utils.reminder.TaskReminderManager;
import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.disposables.CompositeDisposable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import javax.inject.Inject;

@AndroidEntryPoint
public class TaskReminderPickerBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "TaskReminderPicker";
    private static final String ARG_TASK_ID = "taskId";
    private static final String ARG_TASK_TITLE = "taskTitle";
    private static final String ARG_REMINDER_TIME = "reminderTime";
    private static final String ARG_INTERVAL_MINUTES = "intervalMinutes";

    @Inject DataManager dataManager;

    private int taskId;
    private String taskTitle;
    private Long selectedTime = null;
    private int selectedIntervalMinutes = 0;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private View selectedTimeCard;
    private TextView selectedTimeDisplay;
    private MaterialButton btnSave;
    private MaterialButton btnDeleteReminder;
    private MaterialSwitch switchRepeatInterval;
    private ChipGroup intervalChips;
    private View intervalDivider;

    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) saveReminder();
                        else
                            Toast.makeText(
                                            requireContext(),
                                            R.string.reminder_permission_denied,
                                            Toast.LENGTH_SHORT)
                                    .show();
                    });

    public static TaskReminderPickerBottomSheet newInstance(
            int taskId, String taskTitle, Long currentReminderTime, int currentIntervalMinutes) {
        TaskReminderPickerBottomSheet f = new TaskReminderPickerBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_TASK_ID, taskId);
        args.putString(ARG_TASK_TITLE, taskTitle != null ? taskTitle : "");
        if (currentReminderTime != null) args.putLong(ARG_REMINDER_TIME, currentReminderTime);
        args.putInt(ARG_INTERVAL_MINUTES, currentIntervalMinutes);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = requireArguments();
        taskId = args.getInt(ARG_TASK_ID, -1);
        taskTitle = args.getString(ARG_TASK_TITLE, "");
        if (args.containsKey(ARG_REMINDER_TIME)) {
            selectedTime = args.getLong(ARG_REMINDER_TIME);
        }
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
        btnSave = view.findViewById(R.id.btnSave);
        btnDeleteReminder = view.findViewById(R.id.btnDeleteReminder);
        switchRepeatInterval = view.findViewById(R.id.switchRepeatInterval);
        intervalChips = view.findViewById(R.id.intervalChips);
        intervalDivider = view.findViewById(R.id.intervalDivider);

        // Hide repeat section — tasks don't use DAILY/WEEKLY/MONTHLY
        view.findViewById(R.id.repeatLabel).setVisibility(View.GONE);
        view.findViewById(R.id.repeatChips).setVisibility(View.GONE);

        // Wire interval switch
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

        // Pre-fill existing reminder
        if (selectedTime != null && selectedTime > System.currentTimeMillis()) {
            updateTimeDisplay();
            showIntervalSection();
            int existingInterval = requireArguments().getInt(ARG_INTERVAL_MINUTES, 0);
            if (existingInterval > 0) {
                selectedIntervalMinutes = existingInterval;
                switchRepeatInterval.setChecked(true);
                intervalChips.setVisibility(View.VISIBLE);
                setIntervalChip(existingInterval);
            }
            btnDeleteReminder.setVisibility(View.VISIBLE);
            btnSave.setEnabled(true);
        }

        view.findViewById(R.id.presetToday).setOnClickListener(v -> applyPreset(todayEvening()));
        view.findViewById(R.id.presetTomorrow)
                .setOnClickListener(v -> applyPreset(tomorrowMorning()));
        view.findViewById(R.id.btnChooseDate).setOnClickListener(v -> showDatePicker());
        btnSave.setOnClickListener(v -> checkPermissionsAndSave());
        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());
        btnDeleteReminder.setOnClickListener(v -> deleteReminder());

        return view;
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

    private void applyPreset(long time) {
        selectedTime = time;
        updateTimeDisplay();
        showIntervalSection();
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

    private void showIntervalSection() {
        intervalDivider.setVisibility(View.VISIBLE);
        switchRepeatInterval.setVisibility(View.VISIBLE);
    }

    private void updateTimeDisplay() {
        if (selectedTime == null) return;
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault());
        selectedTimeDisplay.setText(fmt.format(new Date(selectedTime)));
        selectedTimeCard.setVisibility(View.VISIBLE);
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
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
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
        final long time = selectedTime;
        final int interval = selectedIntervalMinutes;

        disposables.add(
                dataManager
                        .setTaskReminderFull(taskId, time, interval)
                        .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                        .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> {
                                    Task tempTask = new Task();
                                    tempTask.setId(taskId);
                                    tempTask.setTitle(taskTitle);
                                    tempTask.setReminderTime(time);
                                    tempTask.setReminderIntervalMinutes(interval);
                                    TaskReminderManager.scheduleReminder(
                                            requireContext(), tempTask);

                                    Bundle result = new Bundle();
                                    result.putBoolean("saved", true);
                                    getParentFragmentManager()
                                            .setFragmentResult("taskReminderChanged", result);
                                    dismiss();
                                },
                                e -> Log.e(TAG, "save failed", e)));
    }

    private void deleteReminder() {
        disposables.add(
                dataManager
                        .clearTaskReminder(taskId)
                        .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                        .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> {
                                    TaskReminderManager.cancelReminder(requireContext(), taskId);
                                    Bundle result = new Bundle();
                                    result.putBoolean("saved", false);
                                    getParentFragmentManager()
                                            .setFragmentResult("taskReminderChanged", result);
                                    dismiss();
                                },
                                e -> Log.e(TAG, "delete failed", e)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }
}
