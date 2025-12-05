package com.pasich.mynotes.ui.view.dialogs;


import static android.view.View.GONE;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.slider.Slider;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.dialog.BaseDialogBottomSheets;
import com.pasich.mynotes.base.view.MoreNoteMainActivityView;
import com.pasich.mynotes.base.view.MoreNoteNoteActivityView;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.databinding.DialogMoreNoteBinding;
import com.pasich.mynotes.ui.contract.dialogs.MoreNoteDialogContract;
import com.pasich.mynotes.ui.presenter.dialogs.MoreNoteDialogPresenter;
import com.pasich.mynotes.ui.view.widgets.TwoSideSwitchView;
import com.pasich.mynotes.utils.navigation.GoogleTranslateHelper;
import com.pasich.mynotes.utils.tool.TextStyleTool;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.Flowable;


@AndroidEntryPoint
public class MoreNoteDialog extends BaseDialogBottomSheets implements MoreNoteDialogContract.view {

    private final Note mNote;
    private final boolean newNoteActivity;
    private final RootActivity activityNote;

    @Inject
    public MoreNoteDialogPresenter mPresenter;
    @Inject
    public TextStyleTool textStylePreferences;
    private int positionItem;
    private DialogMoreNoteBinding binding;
    /**
     * Interfaces
     */
    private MoreNoteNoteActivityView noteActivity;
    private MoreNoteMainActivityView mainActivity;

    public MoreNoteDialog(Note note, boolean newNoteActivity, RootActivity activityNote, int position) {
        this.mNote = note;
        this.newNoteActivity = newNoteActivity;
        this.activityNote = activityNote;
        this.positionItem = position;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        vibrateOpenDialog(activityNote == RootActivity.MainActivity);
        setState((BottomSheetDialog) requireDialog());
        binding = DialogMoreNoteBinding.inflate(getLayoutInflater(), container, false);

        mPresenter.attachView(this);
        mPresenter.viewIsReady();
        binding.setNewNote(newNoteActivity);
        binding.setActivityNote(activityNote != RootActivity.MainActivity);
        binding.setNote(mNote);
        binding.setValuesText(mNote.getValue().length() > 1);
        textStylePreferences.addButton(binding.settingsActivity.textStyleItem);
        setHideTextSize();
        setChangeTypeEditor();
        return binding.getRoot();
    }


    @Override
    public void setState(BottomSheetDialog dialog) {
        super.setState(dialog);
    }

    // Ховає зміну шрифту на новій версії редактора
    public void setHideTextSize() {
        binding.settingsActivity.getRoot().setVisibility(activityNote == RootActivity.NoteActivity ? View.VISIBLE : GONE);
    }

    public void setChangeTypeEditor() {
        if (mNote.isAttachments()) {
            binding.changeTypeEditor.setMode(TwoSideSwitchView.Mode.INACTIVE);
            binding.changeTypeEditor.setVisibility(GONE);
            return;
        }
        binding.changeTypeEditor.setMode(activityNote == RootActivity.ExtendedActivity ? TwoSideSwitchView.Mode.EXTENDED : TwoSideSwitchView.Mode.SIMPLE);


    }

    @Override
    public void setSliderValue(int value) {
        if (activityNote == RootActivity.NoteActivity) {
            binding.settingsActivity.textSize.setValue(value);
        }
    }

    @Override
    public void loadingTagsOfChips(Flowable<List<Tag>> tagsList) {
        mPresenter.getCompositeDisposable().add(tagsList.subscribeOn(mPresenter.getSchedulerProvider().io()).observeOn(mPresenter.getSchedulerProvider().ui()).subscribe(this::createChipsTag));
    }


    @Override
    public void initInterfaces() {
        try {
            if (activityNote != RootActivity.MainActivity) {
                if (requireActivity() instanceof MoreNoteNoteActivityView) {
                    noteActivity = (MoreNoteNoteActivityView) requireActivity();
                    mainActivity = null;
                } else {
                    Toast.makeText(requireContext(),
                            R.string.error_dialog_wrong_context, Toast.LENGTH_SHORT).show();
                    dismiss();
                }
            } else {
                if (requireActivity() instanceof MoreNoteMainActivityView) {
                    mainActivity = (MoreNoteMainActivityView) requireActivity();
                    noteActivity = null;
                } else {
                    Toast.makeText(requireContext(),
                            R.string.error_dialog_wrong_context, Toast.LENGTH_SHORT).show();
                    dismiss();
                }
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    R.string.error_dialog_wrong_context, Toast.LENGTH_SHORT).show();
            dismiss();
        }
    }


    @Override
    public void callableCopyNote(long newNoteId) {
        mainActivity.openCopyNote(Math.toIntExact(newNoteId));
    }


    @Override
    public void initListeners() {
        if (mNote == null) {
            return;
        }

        if (activityNote != RootActivity.MainActivity) {
            binding.noSave.setOnClickListener(v -> noteActivity.closeActivityNotSaved());
            binding.settingsActivity.textSize.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                @Override
                public void onStartTrackingTouch(@NonNull Slider slider) {
                }

                @Override
                public void onStopTrackingTouch(@NonNull Slider slider) {
                    mPresenter.editSizeText(Math.round(slider.getValue()));
                }
            });

            binding.settingsActivity.textSize.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser) noteActivity.changeTextSizeOnline(Math.round(value));
            });

            binding.settingsActivity.textStyleItem.setOnClickListener(v -> {
                textStylePreferences.changeArgument();
                noteActivity.changeTextStyle();
            });

        } else {
            binding.actionPanelActivate.setOnClickListener(view -> {
                assert mainActivity != null;
                mainActivity.actionStartNote(mNote, positionItem);
                dismiss();
            });
        }


        binding.share.setVisibility(View.VISIBLE);
        binding.share.setOnClickListener(v -> {
            // Open share options dialog
            ShareOptionsDialog shareDialog = new ShareOptionsDialog(mNote);
            shareDialog.show(getParentFragmentManager(), "ShareOptionsDialog");
            dismiss();
        });

        binding.translateNote.setVisibility(View.VISIBLE);
        binding.translateNote.setOnClickListener(v -> {
            GoogleTranslateHelper.startTranslation(requireActivity(), mNote.getValue());
            dismiss();
        });
        binding.moveToTrash.setOnClickListener(v -> {
            mPresenter.noteMoveToTrash(mNote);
            if (activityNote == RootActivity.MainActivity) {
                mainActivity.callbackDeleteNote(mNote);
                dismiss();
            } else {
                noteActivity.closeActivityNotSaved();
            }

        });

        binding.copyNote.setOnClickListener(v -> {
            if (activityNote != RootActivity.MainActivity) {
                noteActivity.openCopyNote(mNote.getId());
            } else {
                mPresenter.copyNoteMainActivity(mNote);
            }

            dismiss();
        });


        if (mNote.getValue() == null && mNote.getValue().isEmpty()) {
            binding.translateNote.setVisibility(GONE);
            binding.share.setVisibility(GONE);
            binding.copyNote.setVisibility(GONE);
        }
    }


    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);

        mPresenter.detachView();
        if (activityNote != RootActivity.MainActivity) {
            binding.noSave.setOnClickListener(null);
            binding.translateNote.setOnClickListener(null);
            binding.settingsActivity.textStyleItem.setOnClickListener(null);
            noteActivity = null;
        } else {
            mainActivity = null;
            binding.actionPanelActivate.setOnClickListener(null);
            positionItem = 0;
        }


        binding.moveToTrash.setOnClickListener(null);
        binding.copyNote.setOnClickListener(null);
        binding.share.setOnClickListener(null);
    }


    private void createChipsTag(List<Tag> tags) {
        if (!tags.isEmpty()) {
            for (Tag tag : tags) {

                Chip newChip = (Chip) getLayoutInflater().inflate(R.layout.layout_chip_entry, binding.chipGroupSystem, false);
                newChip.setText(getString(R.string.tagHastag, tag.getNameTag()));
                if (mNote.getTag().equals(tag.getNameTag())) {
                    newChip.setChecked(true);
                    binding.chipGroupSystem.addView(newChip, 0);
                } else {
                    binding.chipGroupSystem.addView(newChip);
                }

                newChip.setOnCheckedChangeListener(((buttonView, isChecked) -> selectedTag(tag, isChecked)));
            }
        } else {
            binding.scrollChips.setVisibility(GONE);
        }


    }

    private void selectedTag(Tag tag, boolean checked) {
        if (checked) {
            mPresenter.editTagNote(tag.getNameTag(), mNote.getId());
            if (activityNote != RootActivity.MainActivity)
                noteActivity.changeTag(tag.getNameTag(), true);
        } else {
            mPresenter.removeTagNote(mNote.getId());
            if (activityNote != RootActivity.MainActivity) noteActivity.changeTag("", true);
        }

        if (tag.getVisibility() == 1 && activityNote == RootActivity.MainActivity) {
            dismiss();
        }
    }

    public enum RootActivity {
        MainActivity, NoteActivity, ExtendedActivity
    }
}
