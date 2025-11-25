package com.pasich.mynotes.ui.presenter;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.extendedEditor.models.ParsedNote;
import com.pasich.mynotes.extendedEditor.utils.EditorJsonUtils;
import com.pasich.mynotes.ui.contract.NoteContract;
import com.pasich.mynotes.utils.enums.SaveState;
import com.pasich.mynotes.utils.navigation.NoteExtras;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import java.util.Date;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;


public class NotePresenter extends BasePresenter<NoteContract.view> implements NoteContract.presenter {

    private static final String TAG = "NotePresenter";

    private static final long AUTO_SAVE_DELAY = 2000;
    private final Handler autoSaveHandler;
    // Last successfully saved version of the note
    private final Note savedNote = new Note().create("", "", new Date().getTime(), "");
    private String shareText, assignedTag = "";
    private long idKey;
    private boolean newNoteKey;
    private boolean extendedEditor = false;
    private Runnable autoSaveRunnable;
    private boolean isSavingInProgress = false;
    private boolean pendingClose = false;
    // Note downloaded from the database
    private Note targetNote;

    @Inject
    public NotePresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
        autoSaveHandler = new Handler(Looper.getMainLooper());
    }

    // Getters and Setters
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
        return targetNote;
    }

    public void setNote(Note mNote) {
        this.targetNote = mNote;
    }

    public String getAssignedTagNote() {
        return assignedTag;
    }

    public void setAssignedTagNote(String tag) {
        this.assignedTag = tag == null ? "" : tag;
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

    private void syncSavedSnapshot(Note note) {
        savedNote.setTitle(note.getTitle());
        savedNote.setValue(note.getValue());
        savedNote.setValueJson(note.getValueJson());
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

        // Cancel the previously scheduled backup operation
        if (autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
        }

        updateSaveState(SaveState.PENDING);

        autoSaveRunnable = () -> {
            if (targetNote != null && !isViewDead()) {
                saveNote(targetNote, new NoteContract.AutoSaveCallback() {
                    @Override
                    public void onSuccess() {
                        updateSaveState(SaveState.SAVED);
                        autoSaveHandler.postDelayed(() -> updateSaveState(SaveState.IDLE), 3000);
                    }

                    @Override
                    public void onError(Throwable error) {
                        updateSaveState(SaveState.ERROR);
                        autoSaveHandler.postDelayed(() -> updateSaveState(SaveState.PENDING), 5000);
                    }
                });
            }
        };

        autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_DELAY);
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

        getCompositeDisposable().add(getDataManager().updateNote(note)
                .subscribeOn(getSchedulerProvider().io())
                .observeOn(getSchedulerProvider().ui())
                .subscribe(() -> {
                    syncSavedSnapshot(note);
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
                getCompositeDisposable().add(getDataManager().updateNote(note).subscribeOn(getSchedulerProvider().io()).subscribe(() -> syncSavedSnapshot(note), throwable -> Log.e(TAG, "Emergency update failed", throwable)));
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
            deleteNote(targetNote);
            if (!isViewDead()) getView().closeNoteActivity();
            return;
        }

        // No note → just close
        if (targetNote == null) {
            if (!isViewDead()) getView().closeNoteActivity();
            return;
        }

        // Handle unsaved changes
        if (needsSave()) {

            if (isSavingInProgress) {
                pendingClose = true;
                return;
            }

            pendingClose = true;

            // Extended editor case: no JSON saved yet → just close
            if (savedNote.getValueJson().isEmpty()
                    && extendedEditor) {
                if (!isViewDead()) getView().closeNoteActivity();
                return;
            }

            saveNote(targetNote, new NoteContract.AutoSaveCallback() {
                @Override
                public void onSuccess() {
                    updateSaveState(SaveState.SAVED);
                }

                @Override
                public void onError(Throwable error) {
                    updateSaveState(SaveState.ERROR);
                    autoSaveHandler.postDelayed(() -> {
                        pendingClose = false;
                        if (!isViewDead()) {
                            getView().closeNoteActivity();
                        }
                    }, AUTO_SAVE_DELAY);
                }
            });
            return;
        }

        // Nothing to save → close immediately
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
        setAssignedTagNote(mIntent.getStringExtra(NoteExtras.EXTRA_TAG_NOTE));
        setShareText(mIntent.getStringExtra(NoteExtras.EXTRA_SHARE_TEXT));
        setNewNoteKey(mIntent.getBooleanExtra(NoteExtras.EXTRA_NEW_NOTE, true));

        // For new notes, we create an initial Note object
        if (newNoteKey) {
            targetNote = new Note().create("", shareText != null ? shareText : "", new Date().getTime(), assignedTag);
            updateSaveState(SaveState.IDLE);
        }
    }

    /**
     * Loads a note from the database by its ID.
     * Once received, forwards it to the View, updates the internal targetNote,
     * synchronizes the savedNote snapshot, and resets the save state to IDLE.
     */
    @Override
    public void loadingData(long idNote) {
        Log.e(TAG, "load" + idNote);
        getCompositeDisposable().add(getDataManager()
                .getNoteForId(idNote)
                .subscribeOn(getSchedulerProvider().io())
                .observeOn(getSchedulerProvider().ui())
                .subscribe(note -> {
                    if (note != null && !isViewDead()) {
                        getView().loadingNote(note);
                        setNote(note);
                        // Update saved values on load
                        syncSavedSnapshot(note);
                        updateSaveState(SaveState.IDLE);
                    }
                }, throwable -> Log.e(TAG, "loadingData() failed", throwable)));
    }

    @Override
    public void activateEditNote() {
        getView().activatedActivity();
    }

    @Override
    public void deleteNote(Note note) {
        getCompositeDisposable().add(getDataManager().deleteNote(note).subscribeOn(getSchedulerProvider().io()).subscribe(() -> {
                    // completion ignored intentionally
                },
                throwable -> Log.e(TAG, "deleteNote() failed", throwable)));
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
        if (autoSaveHandler != null && autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
            autoSaveRunnable = null;
        }
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

        // повернненя нотатки з extended в simple при наявності вкладень неможливе
        //  getNote().setHasRichContent(false);

        // emergency save
        if (emergencySave) {
            performEmergencySave(targetNote);
            return;
        }

        onNoteChanged();
    }

}


