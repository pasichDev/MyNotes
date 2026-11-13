package com.pasich.mynotes.ui.view.dialogs.main;

import android.content.Context;
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

/**
 * Options for one tag. The host (the parent fragment or the activity) must implement {@link
 * TagOptionsListener}; it is looked up on attach, so the sheet survives being recreated.
 */
public class TagOptionsBottomSheet extends BaseDialogBottomSheets {

    private static final String ARG_NOTES_COUNT = "notesCount";

    private BottomSheetTagOptionsBinding binding;
    private Tag tag;
    @Nullable private TagOptionsListener listener;
    private int notesCount;

    /** Used by the system to recreate the sheet; use {@link #newInstance} instead. */
    public TagOptionsBottomSheet() {}

    public static TagOptionsBottomSheet newInstance(@NonNull Tag tag, int notesCount) {
        TagOptionsBottomSheet sheet = new TagOptionsBottomSheet();
        Bundle args = new Bundle();
        TagArgs.put(args, tag);
        args.putInt(ARG_NOTES_COUNT, notesCount);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (getParentFragment() instanceof TagOptionsListener) {
            listener = (TagOptionsListener) getParentFragment();
        } else if (context instanceof TagOptionsListener) {
            listener = (TagOptionsListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        tag = TagArgs.get(args);
        notesCount = args == null ? 0 : args.getInt(ARG_NOTES_COUNT);
        if (tag == null || listener == null) dismissAllowingStateLoss();
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
        if (tag == null || listener == null) return;
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
        binding.deleteTag.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteTagClick(tag);
            dismiss();
        });

        binding.renameTag.setOnClickListener(v -> {
            if (listener != null) listener.onRenameTagClick(tag);
            dismiss();
        });

        binding.visibleTag.setOnClickListener(v -> {
            if (listener != null) listener.onToggleVisibilityClick(tag);
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
