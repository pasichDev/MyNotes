package com.pasich.mynotes.ui.view.dialogs.main;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.dialog.BaseDialogBottomSheets;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.DialogDeleteTagBinding;
import com.pasich.mynotes.ui.contract.dialogs.DeleteTagDialogContract;
import com.pasich.mynotes.ui.presenter.dialogs.DeleteTagDialogPresenter;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class DeleteTagDialog extends BaseDialogBottomSheets
        implements DeleteTagDialogContract.view {

    @Inject public DeleteTagDialogPresenter mPresenter;
    private Tag tag;
    private DialogDeleteTagBinding binding;

    /** Used by the system to recreate the dialog; use {@link #newInstance} instead. */
    public DeleteTagDialog() {}

    public static DeleteTagDialog newInstance(@NonNull Tag tag) {
        DeleteTagDialog dialog = new DeleteTagDialog();
        Bundle args = new Bundle();
        TagArgs.put(args, tag);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tag = TagArgs.get(getArguments());
        if (tag == null) dismissAllowingStateLoss();
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = DialogDeleteTagBinding.inflate(getLayoutInflater());
        binding.title.headTextDialog.setText(R.string.deleteTag);
        // Shown once the tag turns out to have notes.
        binding.deleteTagAndNotes.setVisibility(View.GONE);
        if (tag == null) return binding.getRoot();
        mPresenter.attachView(this);
        mPresenter.viewIsReady();
        mPresenter.getLoadCountNotesForTag(tag.getNameTag());
        return binding.getRoot();
    }

    @Override
    public void showNotesCount(int count) {
        if (binding == null) return;
        binding.deleteTagAndNotes.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void initListeners() {
        binding.deleteTag.setOnClickListener(v -> {
            mPresenter.deleteTagUnchecked(tag);
            dismiss();
        });

        binding.deleteTagAndNotes.setOnClickListener(v -> {
            mPresenter.deleteTagAndNotes(tag);
            dismiss();
        });

        binding.cancel.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        mPresenter.detachView();
        if (binding != null) {
            binding.deleteTag.setOnClickListener(null);
            binding.deleteTagAndNotes.setOnClickListener(null);
            binding.cancel.setOnClickListener(null);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mPresenter.detachView();
        binding = null;
    }
}
