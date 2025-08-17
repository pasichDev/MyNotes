package com.pasich.mynotes.ui.presenter;


import android.content.Intent;
import android.graphics.Typeface;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.ui.contract.NoteContract;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;

public class NotePresenter extends BasePresenter<NoteContract.view> implements NoteContract.presenter {

    private String shareText, tagNote;
    private long idKey;
    private Note mNote;
    private boolean exitNoSave = false, newNoteKey;


    @Inject
    public NotePresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }


    @Override
    public void viewIsReady() {
        getView().initParam();
        getView().changeTextStyle();
        getView().changeTextSizeOffline();
        getView().settingsActionBar();
        getView().initTypeActivity();
        getView().initListeners();

    }


    @Override
    public void closeActivity() {
        getView().closeNoteActivity();

    }

    @Override
    public void getLoadIntentData(Intent mIntent) {
        setIdKey(mIntent.getLongExtra("idNote", 0));
        setTagNote(mIntent.getStringExtra("tagNote"));
        setShareText(mIntent.getStringExtra("shareText"));
        setNewNoteKey(mIntent.getBooleanExtra("NewNote", true));
    }

    @Override
    public void loadingData(long idNote) {
        getCompositeDisposable().add(getDataManager().getNoteForId(idNote).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(note -> {
            getView().loadingNote(note);
            setNote(note);
        }));


    }

    @Override
    public void activateEditNote() {
        getView().activatedActivity();
    }

    @Override
    public void createNote(Note note) {
        getCompositeDisposable().add(getDataManager().addNote(note, false).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(aLong -> getView().editIdNoteCreated(aLong)));
    }

    @Override
    public void saveNote(Note note) {
        getCompositeDisposable().add(getDataManager().updateNote(note).subscribeOn(getSchedulerProvider().io()).subscribe());
    }

    @Override
    public void deleteNote(Note note) {
        getCompositeDisposable().add(getDataManager().deleteNote(note).subscribeOn(getSchedulerProvider().io()).subscribe());
    }

    public String getShareText() {
        return shareText;
    }

    public void setShareText(String shareText) {
        this.shareText = shareText == null ? "" : shareText;
    }

    public long getIdKey() {
        return idKey;
    }

    public void setIdKey(long idKey) {
        this.idKey = idKey;
    }

    public Note getNote() {
        return mNote;
    }

    public void setNote(Note mNote) {
        this.mNote = mNote;
    }

    public String getTagNote() {
        return tagNote;
    }

    public void setTagNote(String tagNote) {
        this.tagNote = tagNote == null ? "" : tagNote;
    }

    public boolean getNewNotesKey() {
        return newNoteKey;
    }

    public void setNewNoteKey(boolean newNoteKey) {
        this.newNoteKey = newNoteKey;
    }

    public boolean getExitNoteSave() {
        return exitNoSave;
    }

    public void setExitNoSave(boolean exitNoSave) {
        this.exitNoSave = exitNoSave;
    }

    @Override
    public int getTypeFace(String textStyle) {
        return switch (textStyle) {
            case "italic" -> Typeface.ITALIC;
            case "bold" -> Typeface.BOLD;
            case "bold-italic" -> Typeface.BOLD_ITALIC;
            default -> Typeface.NORMAL;
        };
    }
}
