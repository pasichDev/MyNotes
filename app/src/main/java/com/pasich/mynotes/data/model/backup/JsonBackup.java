package com.pasich.mynotes.data.model.backup;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.TrashNote;

import java.util.ArrayList;
import java.util.List;

public class JsonBackup {

    private PreferencesBackup preferences;
    private List<Note> notes;
    private List<TrashNote> trashNotes;

    private List<Tag> tags;

    private boolean errorCode = false;

    public JsonBackup() {
        this.preferences = new PreferencesBackup();
        this.notes = getNotes();
        this.tags = getTags();
        this.trashNotes = getTrashNotes();
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
