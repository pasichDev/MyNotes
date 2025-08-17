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

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;


@AndroidEntryPoint
public class DeleteTagDialog extends BaseDialogBottomSheets implements DeleteTagDialogContract.view {

    @Inject
    public DeleteTagDialogPresenter mPresenter;
    private Tag tag;
    private DialogDeleteTagBinding binding;


    public DeleteTagDialog(Tag tag) {
        this.tag = tag;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogDeleteTagBinding.inflate(getLayoutInflater());
        mPresenter.attachView(this);
        mPresenter.viewIsReady();
        mPresenter.getLoadCountNotesForTag(tag.getNameTag());
        binding.title.headTextDialog.setText(R.string.deleteTag);
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        binding.deleteTagAndNotes.setVisibility(mPresenter.getCountNotesForTag() > 0 ? View.VISIBLE : View.GONE);
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
        binding.deleteTag.setOnClickListener(null);
        binding.deleteTagAndNotes.setOnClickListener(null);
        binding.cancel.setOnClickListener(null);
        tag = null;
    }
}
