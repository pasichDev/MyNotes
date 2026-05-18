package com.pasich.mynotes.ui.view.dialogs.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.dialog.BaseDialogBottomSheets;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.BottomSheetTagOptionsBinding;

public class TagOptionsBottomSheet extends BaseDialogBottomSheets {

    private BottomSheetTagOptionsBinding binding;
    private final Tag tag;
    private final TagOptionsListener listener;
    private final int notesCount;

    public TagOptionsBottomSheet(Tag tag, int notesCount, TagOptionsListener listener) {
        this.tag = tag;
        this.notesCount = notesCount;
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = BottomSheetTagOptionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setState((BottomSheetDialog) requireDialog());
        vibrateOpenDialog(true);
        setupView();
        setupListeners();
    }

    private void setupView() {
        // Отображаем название тега
        binding.tagTitle.setText(tag.getNameTag());

        // Отображаем количество заметок
        binding.tagNotesCount.setText(
                getResources().getQuantityString(R.plurals.notes_count, notesCount, notesCount));

        // Настраиваем видимость тега
        binding.imageTagVisible.setImageResource(
                tag.getVisibility() == 1 ? R.drawable.ic_show : R.drawable.ic_hide);
        binding.textVisibilityTag.setText(
                tag.getVisibility() == 1 ? R.string.visibleTag : R.string.hiddeTag);
    }

    private void setupListeners() {
        binding.deleteTag.setOnClickListener(
                v -> {
                    listener.onDeleteTagClick(tag);
                    dismiss();
                });

        binding.renameTag.setOnClickListener(
                v -> {
                    listener.onRenameTagClick(tag);
                    dismiss();
                });

        binding.visibleTag.setOnClickListener(
                v -> {
                    listener.onToggleVisibilityClick(tag);
                    dismiss();
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void initListeners() {}

    public interface TagOptionsListener {
        void onDeleteTagClick(Tag tag);

        void onRenameTagClick(Tag tag);

        void onToggleVisibilityClick(Tag tag);
    }
}
