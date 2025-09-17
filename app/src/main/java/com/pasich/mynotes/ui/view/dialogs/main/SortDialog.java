package com.pasich.mynotes.ui.view.dialogs.main;

import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_DEFAULT_SORT_PREF;
import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_PREFERENCE_SORT;
import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_DEFAULT_TAGS_SORT_PREF;
import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_PREFERENCE_TAGS_SORT;

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
import com.pasich.mynotes.base.view.TagsSortView;
import com.pasich.mynotes.databinding.DialogChooseSortBinding;
import com.preference.PowerPreference;


public class SortDialog extends BaseDialogBottomSheets {
    private MainSortView sortView;
    private TagsSortView tagsSortView;
    private DialogChooseSortBinding binding;
    private String sortParam;
    private String tagsSortParam;
    private boolean isTagsSort = false;

    public SortDialog() {
        this.isTagsSort = false;
    }

    public SortDialog(boolean isTagsSort) {
        this.isTagsSort = isTagsSort;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        this.binding = DialogChooseSortBinding.inflate(getLayoutInflater());
        
        if (isTagsSort) {
            this.tagsSortView = (TagsSortView) getContext();
            this.tagsSortParam = PowerPreference.getDefaultFile().getString(ARGUMENT_PREFERENCE_TAGS_SORT, ARGUMENT_DEFAULT_TAGS_SORT_PREF);
            binding.head.setText(R.string.sortHead);
            setupTagsView();
            selectedAutoItemTags(tagsSortParam);
        } else {
            this.sortView = (MainSortView) getContext();
            this.sortParam = PowerPreference.getDefaultFile().getString(ARGUMENT_PREFERENCE_SORT, ARGUMENT_DEFAULT_SORT_PREF);
            binding.head.setText(R.string.sortHead);
            setupNotesView();
            selectedAutoItem(sortParam);
        }
        
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

    private void selectedTagsSort(String param) {
        if (!param.equals(tagsSortParam)) {
            PowerPreference.getDefaultFile().setString(ARGUMENT_PREFERENCE_TAGS_SORT, param);
            assert tagsSortView != null;
            tagsSortView.sortTags(param);
            dismiss();
        }
    }

    private void setupNotesView() {
        // Hide tags sorting options
        binding.TagsPositionSort.setVisibility(View.GONE);
        binding.TagsCreationDateSort.setVisibility(View.GONE);
    }

    private void setupTagsView() {
        // Hide notes sorting options
        binding.DataSort.setVisibility(View.GONE);
        binding.DataReserve.setVisibility(View.GONE);
        binding.TitleSort.setVisibility(View.GONE);
        binding.TitleReserve.setVisibility(View.GONE);
        
        // Show tags sorting options
        binding.TagsPositionSort.setVisibility(View.VISIBLE);
        binding.TagsCreationDateSort.setVisibility(View.VISIBLE);
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

    public void selectedAutoItemTags(String param) {
        int colorBackground = MaterialColors.getColor(requireContext(), R.attr.colorSurfaceVariant, Color.GRAY);
        int colorText = MaterialColors.getColor(requireContext(), R.attr.colorPrimary, Color.BLACK);

        switch (param) {
            case "TagsPositionSort" -> {
                binding.TagsPositionSort.setBackgroundColor(colorBackground);
                binding.TagsPositionSortText.setTextColor(colorText);
                binding.TagsPositionSortCheck.setVisibility(View.VISIBLE);
            }
            case "TagsCreationDateSort" -> {
                binding.TagsCreationDateSort.setBackgroundColor(colorBackground);
                binding.TagsCreationDateSortText.setTextColor(colorText);
                binding.TagsCreationDateSortCheck.setVisibility(View.VISIBLE);
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
        binding.TagsPositionSort.setOnClickListener(null);
        binding.TagsCreationDateSort.setOnClickListener(null);
    }

    @Override
    public void initListeners() {
        if (isTagsSort) {
            // Setup listeners for tags sorting
            binding.TagsPositionSort.setOnClickListener(v -> selectedTagsSort("TagsPositionSort"));
            binding.TagsCreationDateSort.setOnClickListener(v -> selectedTagsSort("TagsCreationDateSort"));
        } else {
            // Setup listeners for notes sorting
            binding.DataSort.setOnClickListener(v -> selectedSort("DataSort"));
            binding.DataReserve.setOnClickListener(v -> selectedSort("DataReserve"));
            binding.TitleSort.setOnClickListener(v -> selectedSort("TitleSort"));
            binding.TitleReserve.setOnClickListener(v -> selectedSort("TitleReserve"));
        }
    }
}
