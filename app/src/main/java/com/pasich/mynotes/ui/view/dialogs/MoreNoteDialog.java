package com.pasich.mynotes.ui.view.dialogs;


import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

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
import com.pasich.mynotes.utils.GoogleTranslationIntent;
import com.pasich.mynotes.utils.tool.TextStyleTool;
import com.pasich.mynotes.ui.view.dialogs.note.NoteBackgroundDialog;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.Flowable;

@AndroidEntryPoint
public class MoreNoteDialog extends BaseDialogBottomSheets implements MoreNoteDialogContract.view {


    private final Note mNote;
    private final boolean newNoteActivity;
    private final boolean activityNote;
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

    public MoreNoteDialog(Note note, boolean newNoteActivity, boolean activityNote, int position) {
        this.mNote = note;
        this.newNoteActivity = newNoteActivity;
        this.activityNote = activityNote;
        this.positionItem = position;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        vibrateOpenDialog(!activityNote);
        setState((BottomSheetDialog) requireDialog());
        binding = DialogMoreNoteBinding.inflate(getLayoutInflater(), container, false);

        mPresenter.attachView(this);
        mPresenter.viewIsReady();
        binding.setNewNote(newNoteActivity);
        binding.setActivityNote(activityNote);
        binding.setValuesText(mNote.getValue().length() > 1);
        textStylePreferences.addButton(binding.settingsActivity.textStyleItem);
        addTitle();
        binding.settingsActivity.rootView.setVisibility(activityNote ? View.VISIBLE : View.GONE);
        
        // Ініціалізуємо інтерфейси
        initInterfaces();
        
        return binding.getRoot();
    }


    @Override
    public void setState(BottomSheetDialog dialog) {
        super.setState(dialog);
    }

    public void addTitle() {
        if (!activityNote) {
            String title = mNote.getTitle().length() > 20 ? mNote.getTitle().substring(0, 20) + "..." : mNote.getTitle();
            binding.includeHead.headTextDialog.setText(mNote.getTitle().length() > 1 ? title : getString(R.string.chooseNote));
            binding.includeHead.getRoot().setVisibility(newNoteActivity ? View.GONE : View.VISIBLE);
            binding.spacerLast.setVisibility(View.GONE);
        } else {
            binding.includeHead.getRoot().setVisibility(View.GONE);
        }
    }

    @Override
    public void setSliderValue(int value) {
        if (activityNote) {
            binding.settingsActivity.textSize.setValue(value);
        }
    }

    @Override
    public void loadingTagsOfChips(Flowable<List<Tag>> tagsList) {
        mPresenter.getCompositeDisposable().add(tagsList.subscribeOn(mPresenter.getSchedulerProvider().io()).observeOn(mPresenter.getSchedulerProvider().ui()).subscribe(this::createChipsTag));


    }

    public void setRippleBottomLayout() {

        if (binding.chipGroupSystem.getChildCount() == 0 && !newNoteActivity) {
            if (activityNote)
                binding.noSave.setBackground(AppCompatResources.getDrawable(requireContext(), R.drawable.item_bottom_ripple));
            else
                binding.moveToTrash.setBackground(AppCompatResources.getDrawable(requireContext(), R.drawable.item_bottom_ripple));

        } else if (newNoteActivity) {
            binding.noSave.setBackground(AppCompatResources.getDrawable(requireContext(), R.drawable.item_bottom_new_ripple));
        }
    }

    @Override
    public void initInterfaces() {
        try {
            if (activityNote) {
                if (requireActivity() instanceof MoreNoteNoteActivityView) {
                    noteActivity = (MoreNoteNoteActivityView) requireActivity();
                    mainActivity = null;
                } else {
                    noteActivity = null;
                }
            } else {
                if (requireActivity() instanceof MoreNoteMainActivityView) {
                    mainActivity = (MoreNoteMainActivityView) requireActivity();
                    noteActivity = null;
                } else {
                    mainActivity = null;
                }
            }
        } catch (Exception e) {
            noteActivity = null;
            mainActivity = null;
        }
    }

    @Override
    public void callableCopyNote(long newNoteId) {
        if (activityNote) {
            noteActivity.openCopyNote(Math.toIntExact(newNoteId));
        } else {
            mainActivity.openCopyNote(Math.toIntExact(newNoteId));
        }
    }


    @Override
    public void initListeners() {

        if (activityNote) {
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
            
            // Обробник для вибору фону нотатки
            binding.noteBackground.setOnClickListener(v -> {
                if (noteActivity != null) {
                    // Зберігаємо посилання на активність
                    final MoreNoteNoteActivityView activityRef = noteActivity;
                    
                    NoteBackgroundDialog backgroundDialog = new NoteBackgroundDialog(mNote, background -> {
                        // Встановлюємо новий фон для нотатки
                        mNote.setBackground(background);
                        // Повідомляємо активність про зміну фону
                        activityRef.changeBackground(background);
                        
                        // Закриваємо діалог з невеликою затримкою, щоб дати час на збереження
                        assert getView() != null;
                        getView().postDelayed(this::dismiss, 100);
                    });
                    backgroundDialog.show(getParentFragmentManager(), "NoteBackgroundDialog");
                } else {
                    Log.w("MoreNoteDialog", "noteActivity is null, cannot open background dialog");
                }
            });

        } else {
            binding.actionPanelActivate.setOnClickListener(view -> {
                assert mainActivity != null;
                mainActivity.actionStartNote(mNote, positionItem);
                dismiss();
            });
        }


        if (mNote.getValue().length() >= 2) {
            binding.share.setVisibility(View.VISIBLE);
            binding.share.setOnClickListener(v -> {
                // Open share options dialog
                ShareOptionsDialog shareDialog = new ShareOptionsDialog(mNote);
                shareDialog.show(getParentFragmentManager(), "ShareOptionsDialog");
                dismiss();
            });

            initTranslate();
            binding.moveToTrash.setOnClickListener(v -> {
                mPresenter.deleteNote(mNote);

                if (!activityNote) {
                    mainActivity.callbackDeleteNote(mNote);
                    dismiss();
                } else {
                    noteActivity.closeActivityNotSaved();
                }

            });

            binding.copyNote.setOnClickListener(v -> {
                mPresenter.copyNote(mNote, activityNote);
                dismiss();
            });

        }

    }


    private void initTranslate() {
        PackageInfo pi = null;
        try {
            pi = requireActivity().getPackageManager().getPackageInfo(GoogleTranslationIntent.packageTranslator, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("Error initTranslate", String.valueOf(e));

        }

        if (pi != null) {
            binding.translateNote.setVisibility(View.VISIBLE);
            binding.translateNote.setOnClickListener(v -> {
                new GoogleTranslationIntent().startTranslation(requireActivity(), mNote.getValue());
                dismiss();
            });
        }
    }




    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);

        mPresenter.detachView();
        if (activityNote) {
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
            binding.scrollChips.setVisibility(View.GONE);
        }

        setRippleBottomLayout();

    }

    private void selectedTag(Tag tag, boolean checked) {
        if (checked) {
            mPresenter.editTagNote(tag.getNameTag(), mNote.getId());
            if (activityNote) noteActivity.changeTag(tag.getNameTag(), true);
        } else {
            mPresenter.removeTagNote(mNote.getId());
            if (activityNote) noteActivity.changeTag("", true);
        }

        if(tag.getVisibility() == 1 && !activityNote){
            dismiss();
        }
    }


}
