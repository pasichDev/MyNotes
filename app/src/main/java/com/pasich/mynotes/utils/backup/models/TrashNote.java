package com.pasich.mynotes.utils.backup.models;

import com.google.gson.annotations.SerializedName;
import com.pasich.mynotes.data.model.Note;

/**
 * Legacy TrashNote model.
 *
 * <p>This class is kept only for backward compatibility with old backup files created before the
 * introduction of "isTrash" field inside the Note model.
 *
 * <p>Once support for old backups is no longer needed, this class can be safely removed.
 */
@Deprecated
public class TrashNote {

    @SerializedName("a")
    public int id;

    @SerializedName("b")
    private String title;

    @SerializedName("c")
    private String value;

    @SerializedName("d")
    private long date;

    private boolean Checked;

    public TrashNote create(String title, String value, long date) {
        this.title = title;
        this.value = value;
        this.date = date;
        this.Checked = false;
        return this;
    }

    public int getId() {
        return this.id;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean getChecked() {
        return this.Checked;
    }

    public void setChecked(boolean arg) {
        this.Checked = arg;
    }

    public long getDate() {
        return this.date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    /**
     * Converts this legacy TrashNote into a modern Note object using the new unified "isTrash"
     * flag.
     */
    public Note toNote() {
        Note note = new Note();

        note.setTitle(this.title);
        note.setValue(this.value);
        note.setDate(this.date);

        // Legacy backups didn't contain these fields
        note.setTag("");
        note.setValueJson("");
        note.setAttachments(null);

        // Important: mark migrated notes as trash in the new format
        note.setTrash(true);

        return note;
    }
}
