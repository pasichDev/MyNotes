package com.pasich.mynotes.utils.backup.models;


import com.google.gson.annotations.SerializedName;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;

import java.util.ArrayList;
import java.util.List;

public class JsonBackup {

    @SerializedName("a")
    private PreferencesBackup preferences;

    @SerializedName("b")
    private List<Note> notes;

    @SerializedName("c")
    @Deprecated
    private List<TrashNote> trashNotes;

    @SerializedName("d")
    private List<Tag> tags;

    @SerializedName("e")
    private boolean errorCode = false;

    @SerializedName("f")
    private List<Note> newTrashNotes;

    public JsonBackup() {
        this.preferences = new PreferencesBackup();
        this.notes = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.trashNotes = new ArrayList<>();
    }

    public JsonBackup error() {
        this.errorCode = true;
        return this;
    }

    public List<Note> getNotes() {
        return notes == null ? new ArrayList<>() : notes;
    }

    public void setNotes(List<Note> notes) {
        this.notes = notes;
    }

    @Deprecated
    public List<TrashNote> getTrashNotes() {
        return trashNotes == null ? new ArrayList<>() : trashNotes;
    }

    @Deprecated
    public void setTrashNotes(List<TrashNote> trashNotes) {
        this.trashNotes = trashNotes;
    }

    public List<Tag> getTags() {
        return tags == null ? new ArrayList<>() : tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    public PreferencesBackup getPreferences() {
        return preferences == null ? new PreferencesBackup() : preferences;
    }

    public void setPreferences(PreferencesBackup preferences) {
        this.preferences = preferences;
    }

    public boolean isError() {
        return errorCode;
    }

    public JsonBackup setError(boolean error) {
        this.errorCode = error;
        return this;
    }

    public List<Note> getNewTrashNotes() {
        return newTrashNotes == null ? new ArrayList<>() : newTrashNotes;
    }

    public void setNewTrashNotes(List<Note> newTrashNotes) {
        this.newTrashNotes = newTrashNotes;
    }

    public List<Note> getTrashNotesUnified() {
        // Якщо новий формат НЕ пустий — використовуємо його
        if (newTrashNotes != null && !newTrashNotes.isEmpty()) {
            return newTrashNotes;
        }

        // Якщо новий пустий, але є старий — значить бекап старий → треба конвертнути
        if (trashNotes != null && !trashNotes.isEmpty()) {
            List<Note> migrated = new ArrayList<>();

            for (TrashNote old : trashNotes) {
                migrated.add(old.toNote());
            }
            return migrated;
        }

        // Обидва пусті
        return new ArrayList<>();
    }

}
