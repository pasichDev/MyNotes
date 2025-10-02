package com.pasich.mynotes.ui.presenter;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.ui.contract.NoteContract;
import com.pasich.mynotes.utils.enums.SaveState;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import java.util.Date;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;

public class NotePresenter extends BasePresenter<NoteContract.view> implements NoteContract.presenter {

    private static final long AUTO_SAVE_DELAY = 2000; // 2 секунди
    // Автозбереження
    private final Handler autoSaveHandler;
    private String shareText, tagNote;
    private long idKey;
    private Note mNote;
    private boolean exitNoSave = false, newNoteKey;
    private Runnable autoSaveRunnable;
    private String lastSavedTitle = "";
    private String lastSavedValue = "";
    private String lastSavedJsonValue = "";

    // Блокування закриття під час збереження
    private boolean isSavingInProgress = false;
    private boolean pendingClose = false;

    private boolean extendedEditor = false;

    @Inject
    public NotePresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
        autoSaveHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onTextChanged() {
        // КРИТИЧНО: Перевіряємо чи Activity ще живе
        if (getView() == null) {
            // Activity знищується, але є зміни - робимо екстрене збереження
            if (hasValidContent(mNote)) {
                performEmergencySave(mNote);
            }
            return;
        }

        // Перевіряємо чи є контент для збереження
        if (!hasValidContent(mNote)) {
            // Якщо нотатка пуста, приховуємо індикатор і не запускаємо збереження
            updateSaveState(SaveState.IDLE);
            return;
        }

        // Скасовуємо попередню заплановану операцію збереження
        if (autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
        }

        // Встановлюємо стан "очікування збереження" тільки якщо є контент
        updateSaveState(SaveState.PENDING);

        // Створюємо нову операцію автозбереження з затримкою
        autoSaveRunnable = () -> {
            if (mNote != null && getView() != null) {
                performAutoSave();
            }
        };

        autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_DELAY);
    }

    private void performAutoSave() {
        if (mNote == null || getView() == null) return;

        autoSaveNote(mNote, new NoteContract.AutoSaveCallback() {
            @Override
            public void onSuccess() {
                updateSaveState(SaveState.SAVED);
                // Через 3 секунди приховуємо індикатор
                autoSaveHandler.postDelayed(() -> updateSaveState(SaveState.IDLE), 3000);
            }

            @Override
            public void onError(Throwable error) {
                updateSaveState(SaveState.ERROR);
                Log.e("NotePresenter", "Auto-save error", error);
                // Через 5 секунд повертаємо до стану "очікування"
                autoSaveHandler.postDelayed(() -> updateSaveState(SaveState.PENDING), 5000);
            }
        });
    }

    @Override
    public void autoSaveNote(Note note, NoteContract.AutoSaveCallback callback) {
        if (note == null) {
            callback.onError(new Exception("Note is null"));
            return;
        }

        // КРИТИЧНО: Перевіряємо чи Activity ще живе перед збереженням
        if (getView() == null) {
            // Activity знищене, але все одно зберігаємо дані синхронно
            performEmergencySave(note);
            return;
        }

        // Перевіряємо чи є валідний контент для збереження
        if (!hasValidContent(note)) {
            updateSaveState(SaveState.IDLE);
            callback.onSuccess();
            return;
        }

        if (note.getId() == 0) {
            // Для нових нотаток
            if (newNoteKey) {
                updateSaveState(SaveState.SAVING);
                // Викликаємо createNote з callback
                createNoteWithCallback(note, callback);
            } else {
                callback.onSuccess();
            }
            return;
        }

        // Перевіряємо чи є зміни
        boolean hasChanges = !note.getTitle().equals(lastSavedTitle) || hasValueChanges();

        if (!hasChanges) {
            updateSaveState(SaveState.IDLE);
            return;
        }

        updateSaveState(SaveState.SAVING);
        note.setDate(new Date().getTime());

        getCompositeDisposable().add(getDataManager().updateNote(note).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
            lastSavedTitle = note.getTitle();
            lastSavedValue = note.getValue();
            lastSavedJsonValue = note.getValueJson();
            callback.onSuccess();
        }, callback::onError));
    }

    private void updateSaveState(SaveState newState) {
        isSavingInProgress = (newState == SaveState.SAVING);

        if (getView() != null) {
            getView().updateSaveStatus(newState);
        }

        // Якщо збереження завершилось і очікується закриття
        if (!isSavingInProgress && pendingClose) {
            pendingClose = false;
            if (getView() != null) {
                getView().closeNoteActivity();
            }
        }
    }

    @Override
    public void closeActivity() {
        // Перевіряємо, чи є незбережені зміни
        if (hasUnsavedChanges()) {
            if (isSavingInProgress) {
                // Якщо зараз відбувається збереження, чекаємо його завершення
                pendingClose = true;
                return;
            } else {
                // Запускаємо збереження перед закриттям
                pendingClose = true;

                //Якщо ця на нотатка не була змінена та остання редакція в простому редаторі то канцесим її, також якщо це розширений редактор
                if (!mNote.hasRichContent() && lastSavedJsonValue.isEmpty() && extendedEditor) {
                    getView().closeNoteActivity();
                    return;
                }

                performFinalSave();
                return;
            }
        }

        // Якщо немає змін, закриваємо одразу
        if (getView() != null) {
            getView().closeNoteActivity();
        }
    }

    private boolean hasUnsavedChanges() {
        if (mNote == null) return false;

        // Спочатку перевіряємо чи є валідний контент
        if (!hasValidContent(mNote)) {
            return false; // Якщо контент пустий, не вважаємо це за зміни
        }

        // Для нових нотаток перевіряємо чи є контент
        if (newNoteKey) {
            return hasValidContent(mNote);
        }

        return hasValueChanges();

    }

    /**
     * Перевіряє чи є зміни в нотатці враховуючи тип редактора
     */
    private boolean hasValueChanges() {
        if (extendedEditor) {
            Gson gson = new Gson();
            JsonElement e1 = JsonParser.parseString(gson.toJson(mNote.getValueJson()));
            JsonElement e2 = JsonParser.parseString(gson.toJson(lastSavedJsonValue));

            return !e1.equals(e2);
        } else {

            return !mNote.getValue().equals(lastSavedValue);
        }
    }


    /**
     * Перевіряє чи має нотатка валідний контент (не пустий заголовок або текст)
     */
    private boolean hasValidContent(Note note) {
        if (note == null) return false;

        String title = note.getTitle() != null ? note.getTitle().trim() : "";
        String value = note.getValue() != null ? note.getValue().trim() : "";

        // Вважаємо контент валідним якщо є хоча б 1 символ в заголовку або тексті
        return !title.isEmpty() || !value.isEmpty();
    }

    private void performFinalSave() {
        if (mNote == null) return;

        updateSaveState(SaveState.SAVING);

        autoSaveNote(mNote, new NoteContract.AutoSaveCallback() {
            @Override
            public void onSuccess() {
                updateSaveState(SaveState.SAVED);
            }

            @Override
            public void onError(Throwable error) {
                updateSaveState(SaveState.ERROR);
                // Навіть при помилці закриваємо через 2 секунди
                autoSaveHandler.postDelayed(() -> {
                    pendingClose = false;
                    if (getView() != null) {
                        getView().closeNoteActivity();
                    }
                }, 2000);
            }
        });
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
    public void getLoadIntentData(Intent mIntent) {
        setIdKey(mIntent.getLongExtra("idNote", 0));
        setTagNote(mIntent.getStringExtra("tagNote"));
        setShareText(mIntent.getStringExtra("shareText"));
        setNewNoteKey(mIntent.getBooleanExtra("NewNote", true));

        // Для нових нотаток створюємо початковий об'єкт Note
        if (newNoteKey) {
            mNote = new Note().create("", shareText != null ? shareText : "", new Date().getTime(), tagNote);
            lastSavedTitle = "";
            lastSavedValue = "";
            lastSavedJsonValue = "";
            updateSaveState(SaveState.IDLE);
        }
    }

    @Override
    public void loadingData(long idNote) {
        getCompositeDisposable().add(getDataManager()
                .getNoteForId(idNote)
                .subscribeOn(getSchedulerProvider().io())
                .observeOn(getSchedulerProvider().ui())
                .subscribe(note -> {
                    if (note != null && getView() != null) {
                        getView().loadingNote(note);
                        setNote(note);
                        // Оновлюємо збережені значення при завантаженні
                        lastSavedTitle = note.getTitle() != null ? note.getTitle() : "";
                        lastSavedValue = note.getValue() != null ? note.getValue() : "";
                        lastSavedJsonValue = note.getValueJson() != null ? note.getValueJson() : "";
                        updateSaveState(SaveState.IDLE);
                    }
                }, throwable -> Log.e("NotePresenter", "Error loading note", throwable)));
    }

    @Override
    public void activateEditNote() {
        getView().activatedActivity();
    }
    // Новий метод створення нотатки з callback'ом для автозбереження
    private void createNoteWithCallback(Note note, NoteContract.AutoSaveCallback callback) {
        getCompositeDisposable().add(getDataManager().addNote(note, false).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(aLong -> {
            note.setId(Math.toIntExact(aLong));
            if (getView() != null) {
                getView().editIdNoteCreated(aLong);
            }
            // Оновлюємо збережені значення після створення
            lastSavedTitle = note.getTitle();
            lastSavedValue = note.getValue();
            lastSavedJsonValue = note.getValueJson();
            setNewNoteKey(false);
            callback.onSuccess();
        }, throwable -> {
            Log.e("NotePresenter", "Error creating note", throwable);
            callback.onError(throwable);
        }));
    }


    @Override
    public void deleteNote(Note note) {
        getCompositeDisposable().add(getDataManager().deleteNote(note).subscribeOn(getSchedulerProvider().io()).subscribe(() -> {
                }, // onComplete
                throwable -> Log.e("NotePresenter", "Error deleting note", throwable)));
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

    /**
     * Очищаємо Handler та callbacks при знищенні Activity
     * КРИТИЧНО для уникнення memory leaks та крашів
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
     * Публічний метод для екстреного збереження з Activity
     */
    public void performEmergencySaveIfNeeded() {
        if (mNote != null && hasUnsavedChanges()) {
            performEmergencySave(mNote);
        }
    }

    /**
     * Екстрене збереження коли Activity знищується але є незбережені дані
     * Виконується синхронно для гарантії збереження
     */
    private void performEmergencySave(Note note) {
        try {
            if (note.getId() == 0) {
                // Нова нотатка - створюємо синхронно
                if (newNoteKey && hasValidContent(note)) {
                    getCompositeDisposable().add(getDataManager().addNote(note, false).subscribeOn(getSchedulerProvider().io()).subscribe(aLong -> {
                        note.setId(Math.toIntExact(aLong));
                        lastSavedTitle = note.getTitle();
                        lastSavedValue = note.getValue();
                        lastSavedJsonValue = note.getValueJson();
                        setNewNoteKey(false);
                    }, throwable -> Log.e("NotePresenter", "Emergency save failed", throwable)));
                }
            } else {
                // Існуюча нотатка - оновлюємо синхронно
                boolean hasChanges = !note.getTitle().equals(lastSavedTitle) || hasValueChanges();
                if (hasChanges && hasValidContent(note)) {
                    note.setDate(new Date().getTime());
                    getCompositeDisposable().add(getDataManager().updateNote(note).subscribeOn(getSchedulerProvider().io()).subscribe(() -> {
                        lastSavedTitle = note.getTitle();
                        lastSavedValue = note.getValue();
                        lastSavedJsonValue = note.getValueJson();
                    }, throwable -> Log.e("NotePresenter", "Emergency update failed", throwable)));
                }
            }
        } catch (Exception e) {
            Log.e("NotePresenter", "Critical: Emergency save crashed", e);
        }
    }

    @Override
    public boolean getExtendedEditor() {
        return this.extendedEditor;
    }

    @Override
    public void setExtendedEditor(boolean extendedEditor) {
        this.extendedEditor = extendedEditor;
    }
}
