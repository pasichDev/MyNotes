package com.pasich.mynotes.ui.presenter;


import android.content.Intent;
import android.graphics.Typeface;
import android.util.Log;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.extendedEditor.models.ParsedNote;
import com.pasich.mynotes.extendedEditor.utils.EditorJsonUtils;
import com.pasich.mynotes.ui.contract.NoteContract;
import com.pasich.mynotes.utils.constants.AutoSave;
import com.pasich.mynotes.utils.enums.SaveState;
import com.pasich.mynotes.utils.navigation.NoteExtras;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.SerialDisposable;
import io.reactivex.subjects.PublishSubject;


public class NotePresenter extends BasePresenter<NoteContract.view> implements NoteContract.presenter {

    private static final String TAG = "NotePresenter";


    private final PublishSubject<Boolean> autoSaveTrigger = PublishSubject.create();
    private final SerialDisposable idleTimer = new SerialDisposable();
    // Last successfully saved version of the note
    private final Note savedNote = new Note().create("", "", new Date().getTime(), "");

    private long idKey;
    private boolean newNoteKey;
    private boolean extendedEditor = false;
    private boolean isSavingInProgress = false;
    private boolean pendingClose = false;
    // Note downloaded from the database
    private Note targetNote;

    @Inject
    public NotePresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }

    @Override
    public void attachView(NoteContract.view v) {
        super.attachView(v);
        getCompositeDisposable().add(idleTimer);
        getCompositeDisposable().add(
            autoSaveTrigger
                .debounce(AutoSave.AUTO_SAVE_DELAY, TimeUnit.MILLISECONDS, getSchedulerProvider().computation())
                .observeOn(getSchedulerProvider().ui())
                .subscribe(
                    ignored -> performAutoSave(),
                    error -> android.util.Log.e(TAG, "autoSave stream error", error)
                )
        );
    }

    public long getIdKey() {
        return idKey;
    }

    public void setIdKey(long idKey) {
        this.idKey = idKey;
    }

    public Note getNote() {
        return targetNote;
    }

    public void setNote(Note mNote) {
        this.targetNote = mNote;
    }

    public boolean getNewNotesKey() {
        return newNoteKey;
    }

    public void setNewNoteKey(boolean newNoteKey) {
        this.newNoteKey = newNoteKey;
    }

    @Override
    public boolean getExtendedEditor() {
        return this.extendedEditor;
    }

    @Override
    public void setExtendedEditor(boolean extendedEditor) {
        this.extendedEditor = extendedEditor;
    }

    @Override
    public boolean hasNote() {
        return targetNote != null;
    }


    /**
     * Trigger auto-save for any text change.
     * <p>
     * Logic:
     * 1) If the note is empty, do not save anything (IDLE).
     * 2) Cancel the previous auto-save if the user continues to enter text.
     * 3) Set status to PENDING — there are unsaved changes.
     * 4) After a pause (AUTO_SAVE_DELAY), execute saveNote().
     * - onSuccess → SAVED → after 3 seconds → IDLE
     * - onError   → ERROR → after 5 seconds → PENDING
     * <p>
     * Works as “smart” autosave: saves only after a pause in typing.
     */

    @Override
    public void onNoteChanged() {
        if (!hasMeaningfulContent(targetNote)) {
            updateSaveState(SaveState.IDLE);
            return;
        }
        updateSaveState(SaveState.PENDING);
        autoSaveTrigger.onNext(true);
    }

    private void performAutoSave() {
        if (targetNote != null && !isViewDead()) {
            saveNote(targetNote, new NoteContract.AutoSaveCallback() {
                @Override
                public void onSuccess() {
                    updateSaveState(SaveState.SAVED);
                    if (!isViewDead()) {
                        getView().runAttachmentsCleanup(targetNote);
                    }
                    idleTimer.set(
                        io.reactivex.Observable.timer(3, TimeUnit.SECONDS, getSchedulerProvider().computation())
                            .observeOn(getSchedulerProvider().ui())
                            .subscribe(ignored -> updateSaveState(SaveState.IDLE))
                    );
                }

                @Override
                public void onError(Throwable error) {
                    updateSaveState(SaveState.ERROR);
                    idleTimer.set(
                        io.reactivex.Observable.timer(5, TimeUnit.SECONDS, getSchedulerProvider().computation())
                            .observeOn(getSchedulerProvider().ui())
                            .subscribe(ignored -> updateSaveState(SaveState.PENDING))
                    );
                }
            });
        }
    }


    /**
     * Saves a note (updates an existing one).
     * <p>
     * Main logic:
     * <p>
     * 1) Null check:
     * - If the note is missing, saving is impossible, so we return an error.
     * <p>
     * 2) Activity lifecycle check:
     * - If View is destroyed, but there is data in the note — emergency saving is performed,
     * which works without UI and without callback.
     * <p>
     * 3) Content check:
     * - If the note is empty (title/value/valueJson), there is no point in saving.
     * We return IDLE and success.
     * <p>
     * 4) Checking for changes:
     * - If the note has not changed compared to the last saved version —
     * we do not perform unnecessary writing to the database.
     * <p>
     * 5) Main saving:
     * - We update the edit date.
     * - Perform updateNote() via RxJava (IO → UI).
     * - After successful saving, update lastSavedXXX
     * so that the system correctly identifies the changed data next time.
     * <p>
     * 6) Error handling:
     * - In case of failure — log and call callback.onError().
     * <p>
     * Note:
     * The method is only responsible for updating an existing note.
     */

    private void saveNote(Note note, NoteContract.AutoSaveCallback callback) {
        if (note == null) {
            callback.onError(new Exception("Note is null"));
            return;
        }

        // Check if Activity is still alive
        // Activity is destroyed, but there are changes - perform emergency saving
        if (isViewDead()) {
            if (hasMeaningfulContent(targetNote)) {
                performEmergencySave(targetNote);
            }
            return;
        }

        // Check if there is valid content to save
        if (!hasMeaningfulContent(note)) {
            updateSaveState(SaveState.IDLE);
            callback.onSuccess();
            return;
        }

        // Checking for changes
        if (!(!note.getTitle().equals(savedNote.getTitle()) || isContentChanged())) {
            updateSaveState(SaveState.IDLE);
            return;
        }

        updateSaveState(SaveState.SAVING);
        note.setDate(new Date().getTime());

        getCompositeDisposable().add(getDataManager().updateNote(note).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
            savedNote.copyFrom(note);
            callback.onSuccess();
        }, error -> {
            Log.e(TAG, "saveNote() failed", error);
            callback.onError(error);
        }));
    }

    /**
     * Emergency saving when Activity is destroyed but there is unsaved data.
     * Performed synchronously to ensure saving.
     * Only NoteActivity (SimpleEditor)
     */
    private void performEmergencySave(Note note) {
        try {
            boolean hasChanges = !note.getTitle().equals(savedNote.getTitle()) || isContentChanged();
            if (hasChanges && hasMeaningfulContent(note)) {
                note.setDate(new Date().getTime());
                getCompositeDisposable().add(getDataManager().updateNote(note).subscribeOn(getSchedulerProvider().io()).subscribe(() -> savedNote.copyFrom(note), throwable -> Log.e(TAG, "Emergency update failed", throwable)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Critical: Emergency save crashed", e);
        }
    }

    /**
     * Updates the save status and notifies the UI.
     * If the save is complete and closing is expected, closes the screen.
     */

    private void updateSaveState(SaveState newState) {
        isSavingInProgress = (newState == SaveState.SAVING);
        if (isViewDead()) {
            return;
        }
        getView().updateSaveStatus(newState);


        // If saving is complete and closing is expected
        if (!isSavingInProgress && pendingClose) {
            pendingClose = false;
            getView().closeNoteActivity();
        }
    }

    /**
     * Handles closing the note screen:
     * - deletes empty new notes,
     * - saves pending changes,
     * - or closes immediately if nothing to save.
     */
    @Override
    public void closeActivity() {

        // Case: new empty note → delete and exit
        if (targetNote != null && newNoteKey && !hasMeaningfulContent(targetNote)) {

            getCompositeDisposable().add(getDataManager().deleteNote(targetNote).subscribeOn(getSchedulerProvider().io()).subscribe(() -> getView().runAttachmentsCleanup(targetNote), throwable -> Log.e(TAG, "deleteNote(): FAILED", throwable)));

            if (!isViewDead()) {
                getView().closeNoteActivity();
            }
            return;
        }

        // No note → just close
        if (targetNote == null) {
            if (!isViewDead()) getView().closeNoteActivity();
            return;
        }

        if (needsSave()) {

            if (isSavingInProgress) {
                pendingClose = true;
                return;
            }

            pendingClose = true;

            // Extended editor "empty JSON" check is WRONG — replace with targetNote
            if (extendedEditor && !hasMeaningfulContent(targetNote)) {
                if (!isViewDead()) getView().closeNoteActivity();
                return;
            }

            saveNote(targetNote, new NoteContract.AutoSaveCallback() {

                @Override
                public void onSuccess() {
                    updateSaveState(SaveState.SAVED);

                    if (pendingClose && !isViewDead()) {
                        pendingClose = false;
                        getView().runAttachmentsCleanup(targetNote);
                        getView().closeNoteActivity();
                    }
                }

                @Override
                public void onError(Throwable error) {
                    updateSaveState(SaveState.ERROR);
                    pendingClose = false;
                }
            });

            return;
        }
        if (!isViewDead()) getView().closeNoteActivity();
    }


    /**
     * Determines if the note requires saving (new or modified).
     */

    private boolean needsSave() {
        if (targetNote == null) return false;

        // No content → definitely nothing to save
        if (!hasMeaningfulContent(targetNote)) return false;

        // New note with content → always unsaved
        if (newNoteKey) return true;

        // For existing ones — compare with saved
        return isContentChanged();
    }


    /**
     * Checks whether content differs from the last saved version.
     */

    private boolean isContentChanged() {
        if (targetNote == null || savedNote == null) return false;

        // Title changed
        if (!targetNote.getTitle().equals(savedNote.getTitle())) return true;

        if (extendedEditor) {
            // JSON changed
            return !targetNote.getValueJson().equals(savedNote.getValueJson());
        } else {
            // Plain text changed
            return !targetNote.getValue().equals(savedNote.getValue());
        }
    }


    /**
     * Checks whether the note contains any meaningful data.
     */

    private boolean hasMeaningfulContent(Note note) {
        if (note == null) return false;

        // Simple editor
        if (!note.getTitle().trim().isEmpty()) return true;
        if (!note.getValue().trim().isEmpty()) return true;

        // Attachments (extended editor too)
        if (note.getAttachments() != null && !note.getAttachments().trim().isEmpty()) {
            return true;
        }

        // Extended editor JSON
        String json = note.getValueJson();
        return json != null && !json.trim().isEmpty() && !json.equals("[]");
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

    /**
     * Loads parameters passed to the Activity via Intent.
     * Initializes presenter fields (id, tag, shared text, creation flag).
     * If this is a new note, creates a fresh Note instance with default values.
     */

    @Override
    public void getLoadIntentData(Intent mIntent) {
        setIdKey(mIntent.getLongExtra(NoteExtras.EXTRA_ID_NOTE, 0));
        setNewNoteKey(mIntent.getBooleanExtra(NoteExtras.EXTRA_NEW_NOTE, true));
    }

    /**
     * Loads a note from the database by its ID.
     * Once received, forwards it to the View, updates the internal targetNote,
     * synchronizes the savedNote snapshot, and resets the save state to IDLE.
     */
    @Override
    public void loadingData(long idNote) {
        getCompositeDisposable().add(getDataManager().getNoteForId(idNote).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(note -> {
            if (note != null && !isViewDead()) {
                getView().loadingNote(note);
                setNote(note);
                // Update saved values on load
                savedNote.copyFrom(note);
                updateSaveState(SaveState.IDLE);
            }
        }, throwable -> Log.e(TAG, "loadingData() failed", throwable)));
    }

    @Override
    public void activateEditNote() {
        getView().activatedActivity();
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

    /**
     * Clean up Handler and callbacks when destroying Activity
     */
    public void cleanupHandlers() {
        // Handler removed; CompositeDisposable.dispose() in detachView() handles cleanup
        pendingClose = false;
        isSavingInProgress = false;
    }


    /**
     * Checks whether the View has already been destroyed.
     * <p>
     * Used before any saving operations:
     * if the Activity/Fragment no longer exists
     */
    private boolean isViewDead() {
        return getView() == null;
    }


    /**
     * Updates note content coming from the advanced (Editor.js) editor.
     *
     * @param title    Optional updated title.
     * @param jsonData Optional Editor.js JSON containing blocks.
     *                 <p>
     *                 If title is provided → update title.
     *                 If jsonData is provided → parse blocks into:
     *                 - plain text      → value
     *                 - attachments     → attachments JSON
     *                 - raw blocks JSON → valueJson
     *                 <p>
     *                 Marks the note as rich-content and triggers auto-save.
     */
    @Override
    public void extendedNoteChange(String title, String jsonData) {
        if (getNote() == null || title == null && jsonData == null) return;

        if (title != null) {
            getNote().setTitle(title);
        }

        if (jsonData != null) {
            ParsedNote parsed = EditorJsonUtils.extendedNoteToOldNote(jsonData);
            String attachmentsJson = parsed.toAttachmentsJson();
            getNote().setValue(parsed.plainText);
            getNote().setAttachments(attachmentsJson);
            getNote().setValueJson(jsonData);
        }

        onNoteChanged();
    }


    /**
     * Updates plain-text note fields (title/value) for the simple editor.
     * <p>
     * If title is not null → updates the title.
     * If value is not null → updates the plain text and clears valueJson.
     * <p>
     * Marks the note as non-rich and triggers auto-save.
     * Passing null means "do not modify this field".
     */
    @Override
    public void simpleNoteChange(String title, String value, boolean emergencySave) {
        if (getNote() == null) {
            return;
        }

        if (title != null) {
            getNote().setTitle(title);
        }

        if (value != null) {
            getNote().setValue(value);
            getNote().setValueJson("");

        }


        // emergency save
        if (emergencySave) {
            performEmergencySave(targetNote);
            return;
        }

        onNoteChanged();
    }

    @Override
    public void copyNoteRequest() {
        if (targetNote == null) return;

        getCompositeDisposable().add(getDataManager().copyNote(targetNote).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(newId -> {

            // load new note
            getCompositeDisposable().add(getDataManager().getNoteForId(newId).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(note -> {
                targetNote = note;
                savedNote.copyFrom(note);
                idKey = newId;
                newNoteKey = false;

                if (!isViewDead()) {
                    if (getExtendedEditor()) {
                        getView().reloadExtendedEditor();
                    }
                    getView().runCopyAnimation();
                    getView().loadingNote(note);
                    getView().onNoteCopied(newId);

                }

            }, err -> Log.e(TAG, "load failed", err)));

        }, err -> Log.e(TAG, "copy failed", err)));
    }

}
