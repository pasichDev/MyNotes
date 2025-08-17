package com.pasich.mynotes.ui.view.dialogs;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pasich.mynotes.base.dialog.BaseDialogBottomSheets;
import com.pasich.mynotes.databinding.DialogThanksDonatBinding;

public class ThanksDonatDialog extends BaseDialogBottomSheets {

    public DialogThanksDonatBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogThanksDonatBinding.inflate(getLayoutInflater());
        initListeners();
        return binding.getRoot();
    }


    public void initListeners() {
        binding.closeDialog.setOnClickListener(v -> dismiss());
    }


    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);

    }
}
