package com.pasich.mynotes.ui.view.dialogs.main;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.dialog.BaseDialogBottomSheets;
import com.pasich.mynotes.databinding.DialogViewOptionsBinding;
import com.pasich.mynotes.utils.constants.settings.SortParam;
import com.pasich.mynotes.utils.tool.FormatListTool;

/**
 * Bottom sheet with the main screen's view options: the sort order of the notes and the list or
 * grid layout. Every choice is reported to the host at once and the sheet stays open, so both can
 * be changed in one visit.
 *
 * <p>The sheet holds no preferences of its own: it shows what it was opened with and the host
 * ({@link Listener}, the activity) saves and applies the choice, so it survives recreation.
 */
public class ViewOptionsDialog extends BaseDialogBottomSheets {

    public static final String TAG = "ViewOptionsDialog";

    private static final String ARG_SORT = "sort";
    private static final String ARG_FORMAT = "format";

    private DialogViewOptionsBinding binding;
    @Nullable private Listener listener;
    private String sort;
    private int format;

    public static ViewOptionsDialog newInstance(String sort, int format) {
        ViewOptionsDialog dialog = new ViewOptionsDialog();
        Bundle args = new Bundle();
        args.putString(ARG_SORT, sort);
        args.putInt(ARG_FORMAT, format);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Listener l) listener = l;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle state = savedInstanceState != null ? savedInstanceState : getArguments();
        sort = state != null ? state.getString(ARG_SORT, SortParam.DataSort) : SortParam.DataSort;
        format =
                state != null
                        ? state.getInt(ARG_FORMAT, FormatListTool.FORMAT_LIST)
                        : FormatListTool.FORMAT_LIST;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = DialogViewOptionsBinding.inflate(inflater, container, false);
        bindState();
        initListeners();
        return binding.getRoot();
    }

    @Override
    public void setState(BottomSheetDialog dialog) {
        super.setState(dialog);
    }

    private void bindState() {
        binding.sortGroup.check(
                SortParam.DataReserve.equals(sort) ? R.id.sortOldest : R.id.sortNewest);
        binding.layoutGroup.check(
                format == FormatListTool.FORMAT_GRID ? R.id.layoutGrid : R.id.layoutList);
    }

    @Override
    public void initListeners() {
        binding.sortGroup.setOnCheckedChangeListener(
                (group, checkedId) -> {
                    String chosen =
                            checkedId == R.id.sortOldest
                                    ? SortParam.DataReserve
                                    : SortParam.DataSort;
                    if (chosen.equals(sort)) return;
                    sort = chosen;
                    if (listener != null) listener.onViewSortSelected(chosen);
                });
        binding.layoutGroup.addOnButtonCheckedListener(
                (group, checkedId, isChecked) -> {
                    if (!isChecked) return;
                    int chosen =
                            checkedId == R.id.layoutGrid
                                    ? FormatListTool.FORMAT_GRID
                                    : FormatListTool.FORMAT_LIST;
                    if (chosen == format) return;
                    format = chosen;
                    if (listener != null) listener.onViewLayoutSelected(chosen);
                });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ARG_SORT, sort);
        outState.putInt(ARG_FORMAT, format);
    }

    @Override
    public void onDestroyView() {
        if (binding != null) {
            binding.sortGroup.setOnCheckedChangeListener(null);
            binding.layoutGroup.clearOnButtonCheckedListeners();
        }
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        listener = null;
        super.onDetach();
    }

    /** Receives the choices made in the sheet; implemented by the host activity. */
    public interface Listener {
        /** {@code sortParam} is one of the notes values of {@link SortParam}. */
        void onViewSortSelected(String sortParam);

        /**
         * {@code format} is {@link FormatListTool#FORMAT_LIST} or {@link
         * FormatListTool#FORMAT_GRID}.
         */
        void onViewLayoutSelected(int format);
    }
}
