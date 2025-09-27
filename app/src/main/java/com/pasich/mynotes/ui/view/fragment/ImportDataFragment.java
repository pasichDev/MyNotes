package com.pasich.mynotes.ui.view.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.pasich.mynotes.databinding.FragmentImportDataBinding;
import com.pasich.mynotes.ui.contract.BackupContract;
import com.pasich.mynotes.ui.view.dialogs.GoogleTakeoutInstructionsBottomSheet;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;


@AndroidEntryPoint
public class ImportDataFragment extends Fragment {

    private FragmentImportDataBinding binding;

    @Inject
    BackupContract.presenter presenter;

    private ActivityResultLauncher<Intent> zipFilePicker;

    public ImportDataFragment() {
    }

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

        zipFilePicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                presenter.startProcessSelectedFileOtherApp(result.getData().getData());
            }
        });
        binding.btnGoogleKeepInstructions.setOnClickListener(v -> GoogleTakeoutInstructionsBottomSheet.newInstance().show(getChildFragmentManager(), "google_takeout_instructions"));

        binding.importFromGoogleKeep.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/zip");
            zipFilePicker.launch(intent);
        });
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}