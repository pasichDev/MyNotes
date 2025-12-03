package com.pasich.mynotes.ui.view.dialogs.main;

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
import com.pasich.mynotes.cache.AppPreferencesCache;
import com.pasich.mynotes.databinding.DialogChooseSortBinding;
import com.pasich.mynotes.utils.constants.settings.SortParam;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SortDialog extends BaseDialogBottomSheets {

    @Inject
    AppPreferencesCache cache;
    private boolean isTagsSort;
    private SortListener listener;
    private DialogChooseSortBinding binding;
    private String sortParam;
    private String tagsSortParam;

    @Inject
    public SortDialog() {
    }

    public static SortDialog newInstance(boolean isTagsSort) {
        SortDialog dialog = new SortDialog();
        Bundle args = new Bundle();
        args.putBoolean("isTagsSort", isTagsSort);
        dialog.setArguments(args);
        return dialog;
    }

    public void setListener(SortListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogChooseSortBinding.inflate(inflater, container, false);
        isTagsSort = getArguments() != null && getArguments().getBoolean("isTagsSort", false);

        if (isTagsSort) {
            tagsSortParam = cache.getTagsSortPref();
            setupTagsView();
            selectedAutoItemTags(tagsSortParam);
        } else {
            sortParam = cache.getSortPref();
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
            cache.setSortPref(param);
            if (listener != null) listener.onSortSelected(param);
            dismiss();
        }
    }

    private void selectedTagsSort(String param) {
        if (!param.equals(tagsSortParam)) {
            cache.setTagsSortPref(param);
            if (listener != null) listener.onTagsSortSelected(param);
            dismiss();
        }
    }

    private void setupNotesView() {
        binding.TagsPositionSort.setVisibility(View.GONE);
        binding.TagsCreationDateSort.setVisibility(View.GONE);
        binding.head.setText(R.string.sortHead);
    }

    private void setupTagsView() {
        binding.DataSort.setVisibility(View.GONE);
        binding.DataReserve.setVisibility(View.GONE);
        binding.TagsPositionSort.setVisibility(View.VISIBLE);
        binding.TagsCreationDateSort.setVisibility(View.VISIBLE);
        binding.head.setText(R.string.sortHead);
    }

    private void selectedAutoItem(String param) {
        int colorBackground = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurfaceVariant, Color.GRAY);
        int colorText = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimaryFixed, Color.BLACK);

        binding.DataSortCheck.setVisibility(View.GONE);
        binding.DataReserveCheck.setVisibility(View.GONE);

        switch (param) {
            case SortParam.DataSort -> {
                binding.DataSort.setBackgroundColor(colorBackground);
                binding.DataSortText.setTextColor(colorText);
                binding.DataSortCheck.setVisibility(View.VISIBLE);
            }
            case SortParam.DataReserve -> {
                binding.DataReserve.setBackgroundColor(colorBackground);
                binding.DataReserveText.setTextColor(colorText);
                binding.DataReserveCheck.setVisibility(View.VISIBLE);
            }
        }
    }

    private void selectedAutoItemTags(String param) {
        int colorBackground = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurfaceVariant, Color.GRAY);
        int colorText = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimaryFixed, Color.BLACK);

        binding.TagsPositionSortCheck.setVisibility(View.GONE);
        binding.TagsCreationDateSortCheck.setVisibility(View.GONE);

        switch (param) {
            case SortParam.TagsPositionSort -> {
                binding.TagsPositionSort.setBackgroundColor(colorBackground);
                binding.TagsPositionSortText.setTextColor(colorText);
                binding.TagsPositionSortCheck.setVisibility(View.VISIBLE);
            }
            case SortParam.TagsCreationDateSort -> {
                binding.TagsCreationDateSort.setBackgroundColor(colorBackground);
                binding.TagsCreationDateSortText.setTextColor(colorText);
                binding.TagsCreationDateSortCheck.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void initListeners() {
        if (isTagsSort) {
            binding.TagsPositionSort.setOnClickListener(v -> selectedTagsSort(SortParam.TagsPositionSort));
            binding.TagsCreationDateSort.setOnClickListener(v -> selectedTagsSort(SortParam.TagsCreationDateSort));
        } else {
            binding.DataSort.setOnClickListener(v -> selectedSort(SortParam.DataSort));
            binding.DataReserve.setOnClickListener(v -> selectedSort(SortParam.DataReserve));
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        clearListeners();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clearListeners();
        binding = null;
    }

    private void clearListeners() {
        if (binding != null) {
            binding.DataSort.setOnClickListener(null);
            binding.DataReserve.setOnClickListener(null);
            binding.TagsPositionSort.setOnClickListener(null);
            binding.TagsCreationDateSort.setOnClickListener(null);
        }
        listener = null;
    }

    public interface SortListener {
        void onSortSelected(String sortParam);

        void onTagsSortSelected(String tagsSortParam);
    }

}
