package com.pasich.mynotes.ui.view.dialogs.note;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pasich.mynotes.R;
import com.pasich.mynotes.databinding.DialogInfoLinkBinding;

public class LinkInfoDialog extends DialogFragment {

    private static final String ARG_LINK = "arg_link";
    private static final String ARG_TYPE = "arg_type";

    private String link;
    DialogInfoLinkBinding binding;
    private int type;
    private final int[] arrayAction = new int[]{R.string.button_url, R.string.button_mail, R.string.button_phone};

    public LinkInfoDialog() {
    }

    public LinkInfoDialog(String link, int type) {
        this();
        Bundle args = new Bundle();
        args.putString(ARG_LINK, link);
        args.putInt(ARG_TYPE, type);
        setArguments(args);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            link = getArguments().getString(ARG_LINK);
            type = getArguments().getInt(ARG_TYPE);
        }
    }

    public static LinkInfoDialog newInstance(String link, int type) {
        LinkInfoDialog dialog = new LinkInfoDialog();
        Bundle args = new Bundle();
        args.putString(ARG_LINK, link);
        args.putInt(ARG_TYPE, type);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder mDialog = new MaterialAlertDialogBuilder(requireContext());
        binding = DialogInfoLinkBinding.inflate(getLayoutInflater());
        mDialog.setView(binding.getRoot());
        binding.headTextDialog.setText(link);
        binding.actionButton.setText(arrayAction[type]);


        binding.copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) requireActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(link, link));
            Toast.makeText(requireContext(), getString(R.string.copyX) + " " + link, Toast.LENGTH_SHORT).show();
            dismiss();
        });

        binding.edit.setOnClickListener(v -> dismiss());

        binding.action.setOnClickListener(v -> {
            Intent intent = null;
            if (type == 1) {
                intent = new Intent(Intent.ACTION_SENDTO).setData(Uri.parse("mailto:" + link));
            }
            if (type == 0) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
            }
            if (type == 2) {
                intent = new Intent(Intent.ACTION_DIAL).setData(Uri.parse("tel:" + link));
            }
            assert intent != null;
            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
            }
            dismiss();
        });

        return mDialog.create();
    }


}
