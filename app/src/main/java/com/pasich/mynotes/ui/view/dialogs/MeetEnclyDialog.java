package com.pasich.mynotes.ui.view.dialogs;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.pasich.mynotes.base.dialog.BaseDialogBottomSheets;
import com.pasich.mynotes.databinding.DialogMeetEnclyBinding;
import com.pasich.mynotes.ui.view.activity.MeetEnclyActivity;

/** The one-time introduction of Encly, shown on the first start of the version that brings it. */
public class MeetEnclyDialog extends BaseDialogBottomSheets {

    private DialogMeetEnclyBinding binding;

    public MeetEnclyDialog() {}

    public static MeetEnclyDialog newInstance() {
        return new MeetEnclyDialog();
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        setState((BottomSheetDialog) requireDialog());
        binding = DialogMeetEnclyBinding.inflate(inflater, container, false);
        initListeners();
        return binding.getRoot();
    }

    @Override
    public void initListeners() {
        binding.moreButton.setOnClickListener(
                v -> {
                    startActivity(new Intent(requireActivity(), MeetEnclyActivity.class));
                    dismiss();
                });
        binding.laterButton.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
