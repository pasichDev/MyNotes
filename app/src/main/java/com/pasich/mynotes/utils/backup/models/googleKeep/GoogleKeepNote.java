package com.pasich.mynotes.utils.backup.models.googleKeep;

import com.google.gson.annotations.SerializedName;
import com.pasich.mynotes.data.model.Note;
import java.util.List;

public class GoogleKeepNote {

    @SerializedName("textContent")
    private String textContent;

    @SerializedName("title")
    private String title;

    @SerializedName("userEditedTimestampUsec")
    private long userEditedTimestampUsec;

    @SerializedName("isTrashed")
    private boolean isTrashed;

    @SerializedName("labels")
    private List<GoogleKeepLabel> labels;

    public String getTextContent() {
        return textContent;
    }

    public String getTitle() {
        return title;
    }

    public long getUserEditedTimestampUsec() {
        return userEditedTimestampUsec;
    }

    public boolean isTrashed() {
        return isTrashed;
    }

    public List<GoogleKeepLabel> getLabels() {
        return labels;
    }

    /** Отримати перший label (або null, якщо їх нема) */
    public GoogleKeepLabel getFirstLabel() {
        if (labels != null && !labels.isEmpty()) {
            return labels.get(0);
        }
        return new GoogleKeepLabel("");
    }

    /** Конвертує нотатку з Google Keep у формат застосунку */
    public Note toAppNotes() {
        Note note = new Note();
        long timestamp = userEditedTimestampUsec / 1000;
        return note.create(title, textContent, timestamp, getFirstLabel().getName());
    }

    public Note toAppNotesTrash() {
        Note note = new Note();
        note.setTrash(true);
        long timestamp = userEditedTimestampUsec / 1000;
        return note.create(title, textContent, timestamp, getFirstLabel().getName());
    }
}
