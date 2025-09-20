package com.pasich.mynotes.data.model.backup.googleKeep;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.TrashNote;

import java.util.ArrayList;
import java.util.List;

public class GoogleKeepImportResult {
    private final List<GoogleKeepNote> notes;
    private final List<GoogleKeepNote> trashedNotes;
    private final List<GoogleKeepLabel> labels;
    private final String error;

    private GoogleKeepImportResult(List<GoogleKeepNote> notes, List<GoogleKeepNote> trashedNotes, List<GoogleKeepLabel> labels, String error) {
        this.notes = notes;
        this.trashedNotes = trashedNotes;
        this.labels = labels;
        this.error = error;
    }

    public static GoogleKeepImportResult success(List<GoogleKeepNote> notes, List<GoogleKeepNote> trashedNotes, List<GoogleKeepLabel> labels) {
        return new GoogleKeepImportResult(notes, trashedNotes, labels, null);
    }

    public static GoogleKeepImportResult error(String error) {
        return new GoogleKeepImportResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), error);
    }

    public List<GoogleKeepNote> getNotes() {
        return notes;
    }

    public List<GoogleKeepNote> getTrashedNotes() {
        return trashedNotes;
    }
    public List<GoogleKeepLabel> getLabels() {
        return labels;
    }

    public String getError() {
        return error;
    }

    public boolean hasError() {
        return error != null;
    }

    public int getTotalNotesCount() {
        return notes.size() + trashedNotes.size();
    }

    // Конвертація активних нотаток у рідні
    public List<Note> toAppNotes() {
        List<Note> appNotes = new ArrayList<>();
        for (GoogleKeepNote note : notes) {
            appNotes.add(note.toAppNote());
        }
        return appNotes;
    }

    // Конвертація видалених нотаток у рідні
    public List<TrashNote> toAppTrashedNotes() {
        List<TrashNote> appNotes = new ArrayList<>();
        for (GoogleKeepNote note : trashedNotes) {
            appNotes.add(note.toAppTrashNote());
        }
        return appNotes;
    }

    // Конвертація тегів у рідні
    public List<Tag> toAppTags() {
        List<Tag> appTags = new ArrayList<>();
        for (GoogleKeepLabel label : labels) {
            appTags.add(new Tag().create(label.getName()));
        }
        return appTags;
    }
}