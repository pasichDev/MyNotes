package com.pasich.mynotes.ui.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.pasich.mynotes.databinding.BottomSheetGoogleTakeoutInstructionsBinding;

public class GoogleTakeoutInstructionsBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetGoogleTakeoutInstructionsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetGoogleTakeoutInstructionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnClose.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public static GoogleTakeoutInstructionsBottomSheet newInstance() {
        return new GoogleTakeoutInstructionsBottomSheet();
    }
}