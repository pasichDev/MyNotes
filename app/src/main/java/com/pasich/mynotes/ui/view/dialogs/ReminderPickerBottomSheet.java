package com.pasich.mynotes.ui.view.dialogs;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.RepeatRule;
import com.pasich.mynotes.utils.reminder.ReminderManager;
import com.pasich.mynotes.utils.reminder.RepeatRuleFormatter;
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
    private Note currentNote;

    /** The reminder as stored when the sheet opened, so re-saving it keeps its anchor. */
    private Long originalTime = null;

    private RepeatRule originalRule = RepeatRule.NONE;

    /** The rule behind the Custom chip, or null while it has none. */
    @Nullable private RepeatRule customRule;

    /** The preset chip to fall back to when the custom dialog is cancelled. */
    private int lastPresetChipId = R.id.chipNone;

    private Chip chipCustom;
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
        getChildFragmentManager()
                .setFragmentResultListener(
                        CustomRepeatDialog.REQUEST_KEY, this, (key, b) -> onCustomRepeat(b));
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
        chipCustom = view.findViewById(R.id.chipCustom);
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

        repeatChips.setOnCheckedStateChangeListener(
                (group, checkedIds) -> {
                    if (!checkedIds.isEmpty() && checkedIds.get(0) != R.id.chipCustom) {
                        lastPresetChipId = checkedIds.get(0);
                    }
                });
        // A click rather than a check: tapping Custom again, already selected, edits the rule.
        chipCustom.setOnClickListener(v -> openCustomRepeat());

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
            originalTime = note.getReminderTime();
            originalRule = RepeatRule.parse(note.getReminderRepeat());
            updateTimeDisplay();
            showRepeatSection();
            showRepeatRule(originalRule);
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

    /**
     * Selects the chip for a rule: a preset when one says the same, otherwise Custom showing the
     * rule itself, "Every 3 hours".
     */
    private void showRepeatRule(@NonNull RepeatRule rule) {
        int preset = presetChipFor(rule);
        if (preset != 0) {
            repeatChips.check(preset);
            return;
        }
        customRule = RepeatRule.every(rule.getInterval(), rule.getUnit(), null);
        String summary = RepeatRuleFormatter.summary(requireContext(), customRule);
        chipCustom.setText(summary);
        chipCustom.setContentDescription(getString(R.string.reminder_repeat_custom_cd, summary));
        repeatChips.check(R.id.chipCustom);
    }

    private static int presetChipFor(@NonNull RepeatRule rule) {
        if (!rule.isRepeating()) return R.id.chipNone;
        if (rule.getInterval() != 1) return 0;
        return switch (rule.getUnit()) {
            case DAYS -> R.id.chipDaily;
            case WEEKS -> R.id.chipWeekly;
            case MONTHS -> R.id.chipMonthly;
            default -> 0;
        };
    }

    @NonNull
    private static RepeatRule presetRule(int chipId) {
        if (chipId == R.id.chipDaily) return RepeatRule.every(1, RepeatRule.Unit.DAYS, null);
        if (chipId == R.id.chipWeekly) return RepeatRule.every(1, RepeatRule.Unit.WEEKS, null);
        if (chipId == R.id.chipMonthly) return RepeatRule.every(1, RepeatRule.Unit.MONTHS, null);
        return RepeatRule.NONE;
    }

    private void openCustomRepeat() {
        if (getChildFragmentManager().findFragmentByTag(CustomRepeatDialog.TAG) != null) return;
        RepeatRule current = customRule != null ? customRule : presetRule(lastPresetChipId);
        CustomRepeatDialog.newInstance(
                        current.isRepeating() ? current.getInterval() : 2,
                        current.isRepeating() ? current.getUnit() : RepeatRule.Unit.DAYS)
                .show(getChildFragmentManager(), CustomRepeatDialog.TAG);
    }

    private void onCustomRepeat(@NonNull Bundle result) {
        if (repeatChips == null) return;
        if (!result.getBoolean(CustomRepeatDialog.RESULT_APPLIED, false)) {
            // Cancelled: back to what was selected before, unless Custom already held a rule.
            if (customRule == null) repeatChips.check(lastPresetChipId);
            return;
        }
        RepeatRule.Unit unit;
        try {
            unit = RepeatRule.Unit.valueOf(result.getString(CustomRepeatDialog.RESULT_UNIT, ""));
        } catch (IllegalArgumentException e) {
            return;
        }
        showRepeatRule(
                RepeatRule.every(result.getInt(CustomRepeatDialog.RESULT_INTERVAL, 1), unit, null));
    }

    /**
     * The chosen rule, anchored at the chosen time. Re-saving without changing the time or the
     * repeat keeps the stored anchor: a monthly reminder for the 31st that now waits for the 30th
     * would otherwise move to the 30th for good.
     */
    @NonNull
    private RepeatRule selectedRule(long time) {
        int checked = repeatChips.getCheckedChipId();
        RepeatRule rule =
                checked == R.id.chipCustom && customRule != null ? customRule : presetRule(checked);
        if (!rule.isRepeating()) return RepeatRule.NONE;
        boolean unchanged =
                originalTime != null
                        && originalTime == time
                        && originalRule.isRepeating()
                        && originalRule.getInterval() == rule.getInterval()
                        && originalRule.getUnit() == rule.getUnit();
        return rule.withAnchor(unchanged ? originalRule.anchorOr(time) : time);
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
        final String repeat = selectedRule(time).serialize();

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
                                    // Redefined: a snooze or repeating notification of the old
                                    // reminder no longer applies.
                                    ReminderManager.cancelReminder(requireContext(), noteId);
                                    boolean exact =
                                            ReminderManager.scheduleReminder(
                                                    requireContext(), tempNote);
                                    if (!exact) askForExactAlarms();

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

    /**
     * The reminder is saved and armed, but without "Alarms & reminders" it may ring late: say so
     * and open the system switch for this app. Granting it re-arms every reminder exactly.
     */
    private void askForExactAlarms() {
        Intent settings = ReminderManager.exactAlarmSettingsIntent(requireContext());
        Toast.makeText(requireContext(), R.string.reminder_exact_alarm_needed, Toast.LENGTH_LONG)
                .show();
        if (settings == null) return;
        try {
            startActivity(settings);
        } catch (android.content.ActivityNotFoundException e) {
            Log.w(TAG, "no exact alarm settings screen", e);
        }
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
