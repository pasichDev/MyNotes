package com.pasich.mynotes.ui.view.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.pasich.mynotes.databinding.FragmentImportDataBinding;
import com.pasich.mynotes.ui.dialogs.GoogleTakeoutInstructionsBottomSheet;

public class ImportDataFragment extends Fragment {

    private FragmentImportDataBinding binding;

    public static ImportDataFragment newInstance() {
        return new ImportDataFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentImportDataBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        binding.btnGoogleKeepInstructions.setOnClickListener(v -> {
            GoogleTakeoutInstructionsBottomSheet.newInstance()
                    .show(getChildFragmentManager(), "google_takeout_instructions");
        });
        
        // Тут буде логіка імпорту в майбутніх версіях
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}