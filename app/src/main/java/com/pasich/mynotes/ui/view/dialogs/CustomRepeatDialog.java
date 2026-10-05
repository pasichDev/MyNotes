package com.pasich.mynotes.ui.view.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.model.RepeatRule;
import com.pasich.mynotes.utils.reminder.RepeatRuleFormatter;

/**
 * "Every [N] [hours / days / weeks / months / years]". The unit choices read "3 hours", "3 days"…
 * for the number typed, and the field's helper line spells out the result, "Every 3 hours".
 *
 * <p>The answer goes to the parent fragment manager under {@link #REQUEST_KEY}; {@link
 * #RESULT_APPLIED} is false when the dialog was cancelled, so the caller can restore its choice.
 */
public class CustomRepeatDialog extends DialogFragment {

    public static final String TAG = "CustomRepeatDialog";
    public static final String REQUEST_KEY = "customRepeat";
    public static final String RESULT_APPLIED = "applied";
    public static final String RESULT_INTERVAL = "interval";
    public static final String RESULT_UNIT = "unit";

    private static final String ARG_INTERVAL = "interval";
    private static final String ARG_UNIT = "unit";

    private static final int[] UNIT_CHIPS = {
        R.id.chipUnitHours,
        R.id.chipUnitDays,
        R.id.chipUnitWeeks,
        R.id.chipUnitMonths,
        R.id.chipUnitYears
    };

    private TextInputLayout intervalLayout;
    private TextInputEditText intervalInput;
    private ChipGroup unitChips;
    private int lastValidInterval;

    public static CustomRepeatDialog newInstance(int interval, @NonNull RepeatRule.Unit unit) {
        CustomRepeatDialog dialog = new CustomRepeatDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_INTERVAL, interval);
        args.putString(ARG_UNIT, unit.name());
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.dialog_repeat_custom, null);
        intervalLayout = view.findViewById(R.id.repeatIntervalLayout);
        intervalInput = view.findViewById(R.id.repeatIntervalInput);
        unitChips = view.findViewById(R.id.repeatUnitChips);

        Bundle args = requireArguments();
        lastValidInterval = clamp(args.getInt(ARG_INTERVAL, 1));
        if (savedInstanceState == null) {
            String text = String.valueOf(lastValidInterval);
            intervalInput.setText(text);
            intervalInput.setSelection(text.length());
            unitChips.check(chipFor(unitFrom(args.getString(ARG_UNIT))));
        }

        AlertDialog dialog =
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.reminder_repeat_custom_title)
                        .setView(view)
                        .setPositiveButton(R.string.reminder_repeat_custom_apply, null)
                        .setNegativeButton(R.string.reminder_cancel, (d, w) -> sendCancelled())
                        .create();

        dialog.setOnShowListener(
                d -> {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                            .setOnClickListener(v -> apply());
                    refresh();
                });
        intervalInput.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

                    @Override
                    public void onTextChanged(CharSequence s, int st, int b, int c) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        refresh();
                    }
                });
        intervalInput.setOnEditorActionListener(
                (v, actionId, event) -> {
                    if (actionId != EditorInfo.IME_ACTION_DONE) return false;
                    apply();
                    return true;
                });
        unitChips.setOnCheckedStateChangeListener((group, ids) -> refresh());
        return dialog;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        sendCancelled();
    }

    private void refresh() {
        if (intervalInput == null) return;
        int typed = typedInterval();
        boolean valid = typed >= RepeatRule.MIN_INTERVAL && typed <= RepeatRule.MAX_INTERVAL;
        if (valid) lastValidInterval = typed;

        RepeatRule.Unit[] units = RepeatRule.Unit.values();
        for (int i = 0; i < UNIT_CHIPS.length; i++) {
            Chip chip = unitChips.findViewById(UNIT_CHIPS[i]);
            chip.setText(
                    RepeatRuleFormatter.unitCount(requireContext(), units[i], lastValidInterval));
        }

        if (valid) {
            intervalLayout.setError(null);
            intervalLayout.setHelperText(
                    RepeatRuleFormatter.summary(
                            requireContext(), RepeatRule.every(typed, selectedUnit(), null)));
        } else {
            intervalLayout.setHelperText(null);
            // An empty field is a number being typed, not a mistake yet.
            intervalLayout.setError(
                    typed == EMPTY ? null : getString(R.string.reminder_repeat_custom_error));
        }
        Dialog dialog = getDialog();
        if (dialog instanceof AlertDialog) {
            Button positive = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
            if (positive != null) positive.setEnabled(valid);
        }
    }

    private void apply() {
        int typed = typedInterval();
        if (typed < RepeatRule.MIN_INTERVAL || typed > RepeatRule.MAX_INTERVAL) return;
        Bundle result = new Bundle();
        result.putBoolean(RESULT_APPLIED, true);
        result.putInt(RESULT_INTERVAL, typed);
        result.putString(RESULT_UNIT, selectedUnit().name());
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        dismiss();
    }

    private void sendCancelled() {
        Bundle result = new Bundle();
        result.putBoolean(RESULT_APPLIED, false);
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
    }

    private static final int EMPTY = -1;

    private int typedInterval() {
        Editable text = intervalInput.getText();
        if (text == null || text.length() == 0) return EMPTY;
        try {
            return Integer.parseInt(text.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @NonNull
    private RepeatRule.Unit selectedUnit() {
        int checked = unitChips.getCheckedChipId();
        for (int i = 0; i < UNIT_CHIPS.length; i++) {
            if (UNIT_CHIPS[i] == checked) return RepeatRule.Unit.values()[i];
        }
        return RepeatRule.Unit.DAYS;
    }

    private static int chipFor(@NonNull RepeatRule.Unit unit) {
        return UNIT_CHIPS[unit.ordinal()];
    }

    @NonNull
    private static RepeatRule.Unit unitFrom(@Nullable String name) {
        try {
            return name != null ? RepeatRule.Unit.valueOf(name) : RepeatRule.Unit.DAYS;
        } catch (IllegalArgumentException e) {
            return RepeatRule.Unit.DAYS;
        }
    }

    private static int clamp(int interval) {
        return Math.max(RepeatRule.MIN_INTERVAL, Math.min(RepeatRule.MAX_INTERVAL, interval));
    }
}
