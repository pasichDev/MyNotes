package com.pasich.mynotes.data.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

import java.util.Objects;

@Entity(tableName = "notes")
public class Note {


    @PrimaryKey(autoGenerate = true)
    @SerializedName("a")
    public int id;

    @SerializedName("b")
    private String title;

    @SerializedName("c")
    private String value;

    @SerializedName("d")
    private long date;

    @SerializedName("e")
    private String tag;

    @SerializedName("f")
    private String valueJson;

    @SerializedName("g")
    private boolean hasRichContent;  // no use

    @SerializedName("h")
    private String attachments; // JSON attachments

    // NEW: instead of TrashNote table
    @SerializedName("i")
    private boolean isTrash = false;
    @Ignore
    private boolean Checked;

    public Note create(String title, String value, long date, String tag) {
        this.title = title;
        this.tag = tag;
        this.value = value;
        this.date = date;
        this.Checked = false;
        return this;
    }

    public Note create(String title, String value, long date) {
        this.title = title;
        this.tag = "";
        this.value = value;
        this.date = date;
        this.Checked = false;
        return this;
    }

    @Deprecated
    public boolean hasRichContent() {
        return hasRichContent;
    }

    @Deprecated
    public void setHasRichContent(boolean hasRichContent) {
        this.hasRichContent = hasRichContent;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return Objects.requireNonNullElse(title, "");
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTag() {
        return this.tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getValue() {
        // or return some default value if you prefer
        return Objects.requireNonNullElse(value, "");
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getValuePreview() {
        if (value == null) {
            return "";  // or return some default value if you prefer
        }
        return value.length() > 400 ? value.substring(0, 400) : value;
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

    public String getValueJson() {
        return Objects.requireNonNullElse(valueJson, "");
    }

    public void setValueJson(String valueJson) {
        this.valueJson = valueJson;
    }


    public boolean isTrash() {
        return isTrash;
    }

    public void setTrash(boolean trash) {
        isTrash = trash;
    }

    public String getAttachments() {
        return attachments;
    }

    public boolean isAttachments() {
        String mAttachments = attachments;
        if (mAttachments == null) return false;

        mAttachments = mAttachments.trim();
        if (mAttachments.isEmpty() || mAttachments.equals("[]")) return false;

        try {
            JsonArray arr = JsonParser.parseString(mAttachments).getAsJsonArray();
            return !arr.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public void setAttachments(String attachments) {
        this.attachments = attachments;
    }


    public void copyFrom(Note other) {
        if (other == null) return;
        this.title = other.title;
        this.value = other.value;
        this.date = other.date;
        this.tag = other.tag;
        this.valueJson = other.valueJson;
        this.hasRichContent = other.hasRichContent;
        this.attachments = other.attachments;
        this.Checked = other.Checked;
    }

    public Note duplicate() {
        Note c = new Note();
        c.setId(0);
        c.setTitle(title);
        c.setValue(value);
        c.setValueJson(valueJson);
        c.setAttachments(attachments);
        c.setTag(tag);
        c.setDate(System.currentTimeMillis());
        c.setTrash(false);
        return c;
    }


}
