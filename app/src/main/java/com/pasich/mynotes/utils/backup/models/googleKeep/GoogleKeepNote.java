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

    /** A checklist note's items; Keep leaves {@code textContent} out for these. */
    @SerializedName("listContent")
    private List<ListItem> listContent;

    /** One line of a Keep checklist. */
    public static class ListItem {
        @SerializedName("text")
        private String text;

        @SerializedName("isChecked")
        private boolean isChecked;
    }

    public String getTextContent() {
        return textContent;
    }

    /**
     * The note's body as plain text.
     *
     * <p>A checklist note carries its items in {@code listContent}, not {@code textContent}. They
     * are written the way the app itself turns a checklist into plain text ("[ ] item", "[x]
     * item"), so the items and their state survive the import instead of arriving as an empty note.
     */
    public String getBodyText() {
        boolean hasText = textContent != null && !textContent.isEmpty();
        if (listContent == null || listContent.isEmpty()) {
            return hasText ? textContent : "";
        }
        StringBuilder body = new StringBuilder();
        if (hasText) {
            body.append(textContent);
        }
        for (ListItem item : listContent) {
            if (item == null) continue;
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(item.isChecked ? "[x] " : "[ ] ")
                    .append(item.text == null ? "" : item.text);
        }
        return body.toString();
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
        return note.create(title, getBodyText(), timestamp, getFirstLabel().getName());
    }

    public Note toAppNotesTrash() {
        Note note = new Note();
        note.setTrash(true);
        long timestamp = userEditedTimestampUsec / 1000;
        return note.create(title, getBodyText(), timestamp, getFirstLabel().getName());
    }
}
