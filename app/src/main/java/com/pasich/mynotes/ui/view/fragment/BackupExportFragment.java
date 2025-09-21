package com.pasich.mynotes.ui.view.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.pasich.mynotes.databinding.FragmentBackupExportBinding;
import com.pasich.mynotes.ui.contract.BackupContract;
import com.pasich.mynotes.ui.presenter.BackupPresenter;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class BackupExportFragment extends Fragment {

    private FragmentBackupExportBinding binding;
    private BackupContract.presenter presenter;

    public static BackupExportFragment newInstance(BackupContract.presenter presenter) {
        BackupExportFragment fragment = new BackupExportFragment();
        fragment.presenter = presenter;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentBackupExportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (presenter != null) {
            binding.setPresenter((BackupPresenter) presenter);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public FragmentBackupExportBinding getBinding() {
        return binding;
    }
}