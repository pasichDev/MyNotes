package com.pasich.mynotes.ui.view.dialogs;

import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.dialog.BaseDialogBottomSheets;
import com.pasich.mynotes.base.view.ShortCutView;
import com.pasich.mynotes.data.model.Label;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.databinding.DialogShortcutBinding;
import com.pasich.mynotes.ui.view.activity.NoteActivity;
import com.pasich.mynotes.utils.adapters.labelAdapter.LabelAdapter;
import com.pasich.mynotes.utils.recycler.SpacesItemDecoration;

import java.util.ArrayList;
import java.util.List;


public class CreateShortcutDialog extends BaseDialogBottomSheets {
    private final Note mNote;
    private DialogShortcutBinding binding;
    private LabelAdapter labelAdapter;
    private ShortCutView shortCutView;
    private boolean errorText = true;

    public CreateShortcutDialog(Note note) {
        this.mNote = note;
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setState((BottomSheetDialog) requireDialog());
        binding = DialogShortcutBinding.inflate(getLayoutInflater());
        binding.title.headTextDialog.setText(R.string.titleDialogShortCut);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            shortCutView = (ShortCutView) requireContext();
            initialTitle();
            setListLabels();
        } else {
            dismiss();
            Toast.makeText(requireContext(), getString(R.string.functionNotSupport), Toast.LENGTH_SHORT).show();
        }


        return binding.getRoot();
    }

    @Override
    public int getTheme() {
        return R.style.bottomSheetInput;
    }

    private void initialTitle() {
        binding.titleShortCut.setText(mNote.getTitle().length() >= 24 ? mNote.getTitle().substring(0, 24) : mNote.getTitle());
    }


    private void setListLabels() {
        labelAdapter = new LabelAdapter(requireContext(), getLabels());
        binding.iconsLabel.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        binding.iconsLabel.addItemDecoration(new SpacesItemDecoration(20));
        binding.iconsLabel.setAdapter(labelAdapter);
        initListeners();
    }


    private void validateText(int length) {
        if (length >= 25 + 1) {
            errorText = true;
            binding.outlinedTextField.setError(getString(R.string.errorMaxNameShortCut));
        } else if (length == (25 + 1) - 1) {
            errorText = false;
            binding.outlinedTextField.setError(null);
        }
        if (length < 1) errorText = true;
        else if (length < (25 + 1) - 1) errorText = false;
    }


    private void createShortCut(String titleShortCut) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            final ShortcutManager shortcutManager = ContextCompat.getSystemService(requireContext(), ShortcutManager.class);
            PendingIntent pendingIntent;


            if (!isCreateShortCutId(shortcutManager != null ? shortcutManager.getPinnedShortcuts() : null)) {
                final ShortcutInfo pinShortcutInfo = new ShortcutInfo.Builder(requireContext(), Integer.toString(mNote.getId())).setShortLabel(titleShortCut).setIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(""), requireContext(), NoteActivity.class).putExtra("NewNote", false).putExtra("idNote", Long.parseLong(String.valueOf(mNote.getId()))).putExtra("shareText", "").putExtra("tagNote", "")).setIcon(Icon.createWithResource(requireContext(), labelAdapter.getSelectLabel().getImage())).build();
                assert shortcutManager != null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    pendingIntent = PendingIntent.getBroadcast(requireContext(), 0, shortcutManager.createShortcutResultIntent(pinShortcutInfo), PendingIntent.FLAG_MUTABLE);
                } else {

                    pendingIntent = PendingIntent.getBroadcast(requireContext(), 0, shortcutManager.createShortcutResultIntent(pinShortcutInfo), 0);
                }
                shortcutManager.requestPinShortcut(pinShortcutInfo, pendingIntent.getIntentSender());
                shortCutView.createShortCut();
            } else {
                shortCutView.shortCutDouble();
            }
        }
    }


    private boolean isCreateShortCutId(@Nullable List<ShortcutInfo> shortcutInfo) {
        if (shortcutInfo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            for (ShortcutInfo info : shortcutInfo) {
                if (Long.parseLong(info.getId()) == mNote.getId()) return true;
            }
        } else {
            return false;
        }
        return false;
    }

    private ArrayList<Label> getLabels() {
        ArrayList<Label> labels = new ArrayList<>();
        labels.add(new Label(R.mipmap.ic_launcher));
        labels.add(new Label(R.mipmap.ic_launcher_note));
        labels.add(new Label(R.mipmap.ic_edit_note));
        labels.add(new Label(R.mipmap.ic_edit_alert));
        return labels;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        binding.createShortCut.setOnClickListener(null);
    }

    @Override
    public void initListeners() {
        labelAdapter.setSelectLabelListener(position -> labelAdapter.selectLabel(position));
        binding.createShortCut.setOnClickListener(v -> {
            if (!errorText) {
                createShortCut(String.valueOf(binding.titleShortCut.getText()));
                dismiss();
            }

        });

        binding.titleShortCut.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                validateText(s.toString().trim().length());
            }
        });
    }
}
