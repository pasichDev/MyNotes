package com.pasich.mynotes.ui.view.dialogs;

import static android.view.View.GONE;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.pasich.mynotes.utils.navigation.GoogleTranslateHelper;
import com.pasich.mynotes.utils.tool.TextStyleTool;
import dagger.hilt.android.AndroidEntryPoint;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;

@AndroidEntryPoint
public class MoreNoteDialog extends BaseDialogBottomSheets implements MoreNoteDialogContract.view {

    @Inject public MoreNoteDialogPresenter mPresenter;
    @Inject public TextStyleTool textStylePreferences;
    private RootActivity rootActivity;
    private int positionItem;
    private DialogMoreNoteBinding binding;

    /** Interfaces */
    private MoreNoteNoteActivityView noteActivity;

    private MoreNoteMainActivityView mainActivity;

    public MoreNoteDialog() {}

    public static MoreNoteDialog newInstance(int noteId, RootActivity root, int position) {
        MoreNoteDialog dialog = new MoreNoteDialog();

        Bundle args = new Bundle();
        args.putInt("noteId", noteId);
        args.putString("root", root.name());
        args.putInt("position", position);

        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args == null) {
            dismiss();
            return;
        }

        rootActivity = RootActivity.valueOf(args.getString("root"));
        positionItem = args.getInt("position");
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        vibrateOpenDialog(rootActivity == RootActivity.MainActivity);
        setState((BottomSheetDialog) requireDialog());
        binding = DialogMoreNoteBinding.inflate(getLayoutInflater(), container, false);

        mPresenter.attachView(this);

        assert getArguments() != null;
        mPresenter.loadNote(getArguments().getInt("noteId"));
        mPresenter.viewIsReady();
        binding.setActivityNote(rootActivity != RootActivity.MainActivity);

        textStylePreferences.addButton(binding.settingsActivity.textStyleItem);

        if (rootActivity != RootActivity.MainActivity) {
            binding.setReminder.setBackgroundResource(R.drawable.bg_item_full);
        }

        return binding.getRoot();
    }

    @Override
    public void setState(BottomSheetDialog dialog) {
        super.setState(dialog);
    }

    @Override
    public void close() {
        dismiss();
    }

    @Override
    public void onNoteLoaded(Note mNote) {
        binding.setNote(mPresenter.getNote());
        binding.setValuesText(!mPresenter.getNote().getValue().isEmpty());
        setHideTextSize();
        setChangeTypeEditor();
        goneCopyNotesExtended();
        updateReminderMenuState(mPresenter.getNote());
        updatePinState(mPresenter.getNote());
        initListeners();

        mPresenter.requestTagsOneShot();
    }

    private void updatePinState(Note note) {
        if (note == null) return;
        boolean pinned = note.isPinned();
        String pinLabel = pinned ? getString(R.string.unpinNote) : getString(R.string.pinNote);
        if (rootActivity == RootActivity.MainActivity) {
            binding.pinNoteText.setText(pinLabel);
        } else {
            binding.quickPinLabel.setText(pinLabel);
        }
    }

    private void updateReminderMenuState(Note note) {
        boolean hasReminder = note != null && note.hasReminder();
        if (hasReminder) {
            SimpleDateFormat fmt = new SimpleDateFormat("d MMM · HH:mm", Locale.getDefault());
            binding.reminderSubtitle.setText(fmt.format(new Date(note.getReminderTime())));
            binding.reminderSubtitle.setVisibility(View.VISIBLE);
        } else {
            binding.reminderSubtitle.setVisibility(View.GONE);
        }
    }

    public void setHideTextSize() {
        boolean showSettings = rootActivity == RootActivity.NoteActivity;
        binding.settingsActivityWrapper.setVisibility(showSettings ? View.VISIBLE : View.GONE);
    }

    public void setChangeTypeEditor() {
        if (rootActivity == RootActivity.MainActivity) return;
        if (mPresenter.getNote().isAttachments()) {
            binding.editorSwitchRow.setVisibility(GONE);
            return;
        }
        String modeLabel =
                rootActivity == RootActivity.ExtendedActivity
                        ? getString(R.string.mode_extended)
                        : getString(R.string.mode_simple);
        binding.editorTypeLabel.setText(modeLabel);
    }

    public void goneCopyNotesExtended() {
        boolean hasAttachments = mPresenter.getNote().isAttachments();
        if (rootActivity == RootActivity.MainActivity) {
            binding.copyNote.setVisibility(hasAttachments ? GONE : View.VISIBLE);
        } else {
            binding.quickCopy.setVisibility(hasAttachments ? GONE : View.VISIBLE);
        }
    }

    @Override
    public void setSliderValue(int value) {
        if (rootActivity == RootActivity.NoteActivity) {
            binding.settingsActivity.textSize.setValue(value);
        }
    }

    @Override
    public void initInterfaces() {
        Activity activity = requireActivity();

        if (activity instanceof MoreNoteNoteActivityView) {
            noteActivity = (MoreNoteNoteActivityView) activity;
            mainActivity = null;
            return;
        }

        if (activity instanceof MoreNoteMainActivityView) {
            mainActivity = (MoreNoteMainActivityView) activity;
            noteActivity = null;
            return;
        }

        Toast.makeText(requireContext(), R.string.error_dialog_wrong_context, Toast.LENGTH_SHORT)
                .show();

        dismiss();
    }

    @Override
    public void callableCopyNote(long newNoteId) {
        if (mainActivity != null) {
            mainActivity.openCopyNote(Math.toIntExact(newNoteId));
        }
    }

    @Override
    public void initListeners() {
        if (mPresenter.getNote() == null) {
            return;
        }

        if (rootActivity != RootActivity.MainActivity) {
            binding.settingsActivity.textSize.addOnSliderTouchListener(
                    new Slider.OnSliderTouchListener() {
                        @Override
                        public void onStartTrackingTouch(@NonNull Slider slider) {}

                        @Override
                        public void onStopTrackingTouch(@NonNull Slider slider) {
                            mPresenter.editSizeText(Math.round(slider.getValue()));
                        }
                    });

            binding.settingsActivity.textSize.addOnChangeListener(
                    (slider, value, fromUser) -> {
                        if (fromUser) noteActivity.changeTextSizeOnline(Math.round(value));
                    });

            binding.settingsActivity.textStyleItem.setOnClickListener(
                    v -> {
                        textStylePreferences.changeArgument();
                        noteActivity.changeTextStyle();
                    });

            binding.editorSwitchRow.setOnClickListener(
                    v -> {
                        if (!isAdded() || noteActivity == null || mPresenter.getNote() == null)
                            return;
                        noteActivity.changeEditor(mPresenter.getNote().getId());
                        dismiss();
                    });

            binding.quickShare.setOnClickListener(
                    v -> {
                        ShareOptionsDialog shareDialog =
                                new ShareOptionsDialog(mPresenter.getNote());
                        shareDialog.show(getParentFragmentManager(), "ShareOptionsDialog");
                        dismiss();
                    });

            binding.quickCopy.setOnClickListener(
                    v -> {
                        if (mPresenter.getNote().isAttachments()) return;
                        noteActivity.openCopyNote(mPresenter.getNote().getId());
                        dismiss();
                    });

            binding.quickTranslate.setOnClickListener(
                    v -> {
                        GoogleTranslateHelper.startTranslation(
                                requireActivity(), mPresenter.getNote().getValue());
                        dismiss();
                    });

            binding.quickPin.setOnClickListener(
                    v -> {
                        mPresenter.togglePinNote();
                        dismiss();
                    });

            if (mPresenter.getNote().getValue().isEmpty()) {
                binding.quickShare.setVisibility(GONE);
                binding.quickCopy.setVisibility(GONE);
            }

        } else {
            binding.actionPanelActivate.setOnClickListener(
                    view -> {
                        assert mainActivity != null;
                        mainActivity.actionStartNote(mPresenter.getNote(), positionItem);
                        dismiss();
                    });

            binding.share.setOnClickListener(
                    v -> {
                        ShareOptionsDialog shareDialog =
                                new ShareOptionsDialog(mPresenter.getNote());
                        shareDialog.show(getParentFragmentManager(), "ShareOptionsDialog");
                        dismiss();
                    });

            binding.copyNote.setOnClickListener(
                    v -> {
                        if (mPresenter.getNote().isAttachments()) return;
                        mPresenter.copyNoteMainActivity();
                        dismiss();
                    });

            binding.pinNote.setOnClickListener(
                    v -> {
                        mPresenter.togglePinNote();
                        dismiss();
                    });

            if (mPresenter.getNote().getValue().isEmpty()) {
                binding.share.setVisibility(GONE);
                binding.copyNote.setVisibility(GONE);
            }
        }

        binding.setReminder.setOnClickListener(
                v -> {
                    if (mPresenter.getNote() == null) return;
                    ReminderPickerBottomSheet.newInstance(mPresenter.getNote().getId())
                            .show(getParentFragmentManager(), "ReminderPicker");
                    dismiss();
                });

        binding.moveToTrash.setOnClickListener(
                v -> {
                    mPresenter.noteMoveToTrash();
                    if (rootActivity == RootActivity.MainActivity) {
                        mainActivity.callbackDeleteNote(mPresenter.getNote());
                        dismiss();
                    } else {
                        noteActivity.closeActivityNotSaved();
                    }
                });
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);

        mPresenter.detachView();
        if (rootActivity != RootActivity.MainActivity) {
            binding.editorSwitchRow.setOnClickListener(null);
            binding.quickShare.setOnClickListener(null);
            binding.quickCopy.setOnClickListener(null);
            binding.quickTranslate.setOnClickListener(null);
            binding.quickPin.setOnClickListener(null);
            binding.settingsActivity.textStyleItem.setOnClickListener(null);
            noteActivity = null;
        } else {
            mainActivity = null;
            binding.actionPanelActivate.setOnClickListener(null);
            binding.share.setOnClickListener(null);
            binding.copyNote.setOnClickListener(null);
            binding.pinNote.setOnClickListener(null);
            positionItem = 0;
        }

        binding.setReminder.setOnClickListener(null);
        binding.moveToTrash.setOnClickListener(null);
    }

    @Override
    public void createChipsTag(List<Tag> tags) {
        binding.chipGroupSystem.removeAllViews();

        if (tags == null || tags.isEmpty()) {
            binding.scrollChips.setVisibility(View.GONE);
            return;
        }

        binding.scrollChips.setVisibility(View.VISIBLE);

        String noteTag = mPresenter.getNote().getTag();

        for (Tag tag : tags) {
            Chip chip =
                    (Chip)
                            getLayoutInflater()
                                    .inflate(
                                            R.layout.layout_chip_entry,
                                            binding.chipGroupSystem,
                                            false);

            chip.setText(getString(R.string.tagHastag, tag.getNameTag()));

            boolean checked = TextUtils.equals(noteTag, tag.getNameTag());
            chip.setChecked(checked);

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> selectedTag(tag, isChecked));

            binding.chipGroupSystem.addView(chip);
        }
    }

    private void selectedTag(Tag tag, boolean checked) {
        final int noteId = mPresenter.getNote().getId();
        if (checked) {
            mPresenter.editTagNote(tag.getNameTag(), noteId);
            if (rootActivity != RootActivity.MainActivity)
                noteActivity.changeTag(tag.getNameTag(), true);
        } else {
            mPresenter.removeTagNote(noteId);
            if (rootActivity != RootActivity.MainActivity) noteActivity.changeTag("", true);
        }

        if (tag.getVisibility() == 1 && rootActivity == RootActivity.MainActivity) {
            dismiss();
        }
    }

    public enum RootActivity {
        MainActivity,
        NoteActivity,
        ExtendedActivity
    }
}
