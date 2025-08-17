package com.pasich.mynotes.ui.view.dialogs.main;

import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_DEFAULT_SORT_PREF;
import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_PREFERENCE_SORT;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.dialog.BaseDialogBottomSheets;
import com.pasich.mynotes.base.view.MainSortView;
import com.pasich.mynotes.databinding.DialogChooseSortBinding;
import com.preference.PowerPreference;


public class SortDialog extends BaseDialogBottomSheets {
    private MainSortView sortView;
    private DialogChooseSortBinding binding;
    private String sortParam;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        this.binding = DialogChooseSortBinding.inflate(getLayoutInflater());
        this.sortView = (MainSortView) getContext();
        this.sortParam = PowerPreference.getDefaultFile().getString(ARGUMENT_PREFERENCE_SORT, ARGUMENT_DEFAULT_SORT_PREF);
        binding.head.setText(R.string.sortHead);
        selectedAutoItem(sortParam);
        initListeners();
        return binding.getRoot();
    }



    @Override
    public void setState(BottomSheetDialog dialog) {
        super.setState(dialog);
    }

    private void selectedSort(String param) {
        if (!param.equals(sortParam)) {
            PowerPreference.getDefaultFile().setString(ARGUMENT_PREFERENCE_SORT, param);
            assert sortView != null;
            sortView.sortList(param);
            dismiss();
        }
    }

    public void selectedAutoItem(String param) {
        int colorBackground = MaterialColors.getColor(requireContext(), R.attr.colorSurfaceVariant, Color.GRAY);
        int colorText = MaterialColors.getColor(requireContext(), R.attr.colorPrimary, Color.BLACK);

        switch (param) {
            case "DataSort" -> {
                binding.DataSort.setBackgroundColor(colorBackground);
                binding.DataSortText.setTextColor(colorText);
                binding.DataSortCheck.setVisibility(View.VISIBLE);
            }
            case "DataReserve" -> {
                binding.DataReserve.setBackgroundColor(colorBackground);
                binding.DataReserveText.setTextColor(colorText);
                binding.DataReserveCheck.setVisibility(View.VISIBLE);
            }
            case "TitleSort" -> {
                binding.TitleSort.setBackgroundColor(colorBackground);
                binding.TitleSortText.setTextColor(colorText);
                binding.TitleSortCheck.setVisibility(View.VISIBLE);
            }
            case "TitleReserve" -> {
                binding.TitleReserve.setBackgroundColor(colorBackground);
                binding.TitleReserveText.setTextColor(colorText);
                binding.TitleReserveCheck.setVisibility(View.VISIBLE);
            }
        }


    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        binding.DataSort.setOnClickListener(null);
        binding.DataReserve.setOnClickListener(null);
        binding.TitleSort.setOnClickListener(null);
        binding.TitleReserve.setOnClickListener(null);

    }

    @Override
    public void initListeners() {
        binding.DataSort.setOnClickListener(v -> selectedSort("DataSort"));
        binding.DataReserve.setOnClickListener(v -> selectedSort("DataReserve"));
        binding.TitleSort.setOnClickListener(v -> selectedSort("TitleSort"));
        binding.TitleReserve.setOnClickListener(v -> selectedSort("TitleReserve"));
    }
}
