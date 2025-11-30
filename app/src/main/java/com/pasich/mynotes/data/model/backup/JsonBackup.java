package com.pasich.mynotes.data.model.backup;

import com.google.gson.annotations.SerializedName;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.deprecated.TrashNote;

import java.util.ArrayList;
import java.util.List;

public class JsonBackup {

    @SerializedName("a")
    private PreferencesBackup preferences;

    @SerializedName("b")
    private List<Note> notes;

    @SerializedName("c")
    private List<TrashNote> trashNotes;

    @SerializedName("d")
    private List<Tag> tags;

    @SerializedName("e")
    private boolean errorCode = false;

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

    public void setNotes(List<Note> notes) {
        this.notes = notes;
    }

    public void setTrashNotes(List<TrashNote> trashNotes) {
        this.trashNotes = trashNotes;
    }


    public List<Note> getNotes() {
        return notes == null ? new ArrayList<>() : notes;
    }

    public List<TrashNote> getTrashNotes() {
        return trashNotes == null ? new ArrayList<>() : trashNotes;
    }

    public List<Tag> getTags() {
        return tags == null ? new ArrayList<>() : tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    public void setPreferences(PreferencesBackup preferences) {
        this.preferences = preferences;
    }

    public PreferencesBackup getPreferences() {
        return preferences == null ? new PreferencesBackup() : preferences;
    }

    public boolean isError() {
        return errorCode;
    }

    public JsonBackup setError(boolean error) {
        this.errorCode = error;
        return this;
    }
}
