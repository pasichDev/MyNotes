package com.pasich.mynotes.ui.presenter.dialogs;

import android.util.Log;
import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.ui.contract.dialogs.MoreNoteDialogContract;
import com.pasich.mynotes.utils.TagsSorter;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import io.reactivex.disposables.CompositeDisposable;
import java.util.Date;
import javax.inject.Inject;

public class MoreNoteDialogPresenter extends BasePresenter<MoreNoteDialogContract.view>
        implements MoreNoteDialogContract.presenter {
    private static final String TAG = "MoreNoteDialogPresenter";
    private Note mNote;

    @Inject
    public MoreNoteDialogPresenter(
            SchedulerProvider schedulerProvider,
            CompositeDisposable compositeDisposable,
            DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }

    @Override
    public void viewIsReady() {
        getView().initInterfaces();
        int textSize = getDataManager().getSizeTextNoteActivity();
        getView().setSliderValue(PreferencesConfig.normalizeNoteTextSize(textSize));
    }

    @Override
    public Note getNote() {
        return mNote;
    }

    @Override
    public void loadNote(int noteId) {
        if (noteId <= 0) {
            if (isViewAttached()) getView().close();
            return;
        }

        getCompositeDisposable()
                .add(
                        getDataManager()
                                .getNoteForId(noteId)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        note -> {
                                            if (note != null && isViewAttached()) {
                                                mNote = note;
                                                getView().onNoteLoaded(note);
                                            }
                                        },
                                        error -> {
                                            Log.e(TAG, "loadNote failed", error);
                                            if (isViewAttached()) getView().close();
                                        }));
    }

    @Override
    public void noteMoveToTrash() {
        if (mNote == null) return;
        DetachedWrites.run(
                getDataManager().moveNoteToTrash(mNote.getId()),
                getSchedulerProvider().io(),
                TAG,
                "noteMoveToTrash");
    }

    @Override
    public void editSizeText(int value) {
        getDataManager().editSizeTextNoteActivity(value);
    }

    @Override
    public void removeTagNote(int idNote) {
        DetachedWrites.run(
                getDataManager().setTagNote("", idNote),
                getSchedulerProvider().io(),
                TAG,
                "removeTagNote");
    }

    @Override
    public void editTagNote(String nameTag, int idNote) {
        DetachedWrites.run(
                getDataManager().setTagNote(nameTag, idNote),
                getSchedulerProvider().io(),
                TAG,
                "editTagNote");
    }

    /**
     * Copies the note and hands the copy's id to {@code onCopied} on the main thread. The copy is
     * not tied to the dialog, which closes before the copy is written.
     */
    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void copyNoteMainActivity(MoreNoteDialogContract.OnNoteCopied onCopied) {
        if (mNote == null) return;
        getDataManager()
                .addNote(
                        new Note()
                                .create(
                                        mNote.getTitle() + " (copy)",
                                        mNote.getValue() + " ",
                                        new Date().getTime(),
                                        mNote.getTag()))
                .subscribeOn(getSchedulerProvider().io())
                .observeOn(getSchedulerProvider().ui())
                .subscribe(
                        onCopied::onCopied,
                        throwable -> Log.e(TAG, "Error copying note", throwable));
    }

    @Override
    public void togglePinNote() {
        if (mNote == null) return;
        boolean newPin = !mNote.isPinned();
        mNote.setPinned(newPin);
        DetachedWrites.run(
                getDataManager().setPinNote(mNote.getId(), newPin),
                getSchedulerProvider().io(),
                TAG,
                "togglePinNote");
    }

    @Override
    public void requestTagsOneShot() {
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .getTagsUser()
                                .take(1)
                                .map(tags ->
                                        TagsSorter.sortTags(
                                                tags, getDataManager().getSortParamTags()))
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        sorted -> getView().createChipsTag(sorted),
                                        err -> Log.e(TAG, "requestTagsOneShot()", err)));
    }
}
