package com.pasich.mynotes.data.model;

import androidx.room.Entity;
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
    private boolean hasRichContent; // no use

    @SerializedName("h")
    private String attachments; // JSON attachments

    // NEW: instead of TrashNote table
    @SerializedName("i")
    private boolean isTrash = false;

    @SerializedName("j")
    @androidx.annotation.Nullable
    @androidx.room.ColumnInfo(name = "reminderTime")
    private Long reminderTime;

    @SerializedName("l")
    @androidx.room.ColumnInfo(name = "isPinned")
    private boolean isPinned = false;

    @SerializedName("k")
    @androidx.annotation.NonNull
    @androidx.room.ColumnInfo(name = "reminderRepeat")
    private String reminderRepeat = "NONE";

    @SerializedName("m")
    @androidx.room.ColumnInfo(name = "reminderIntervalMinutes")
    private int reminderIntervalMinutes = 0;

    public Note create(String title, String value, long date, String tag) {
        this.title = title;
        this.tag = tag;
        this.value = value;
        this.date = date;
        return this;
    }

    public Note create(String title, String value, long date) {
        this.title = title;
        this.tag = "";
        this.value = value;
        this.date = date;
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
        return Objects.requireNonNullElse(this.tag, "");
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
            return ""; // or return some default value if you prefer
        }
        return value.length() > 400 ? value.substring(0, 400) : value;
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

    public Long getReminderTime() {
        return reminderTime;
    }

    public void setReminderTime(Long reminderTime) {
        this.reminderTime = reminderTime;
    }

    public String getReminderRepeat() {
        return reminderRepeat != null ? reminderRepeat : "NONE";
    }

    public void setReminderRepeat(String reminderRepeat) {
        this.reminderRepeat = reminderRepeat != null ? reminderRepeat : "NONE";
    }

    public boolean hasReminder() {
        return reminderTime != null && reminderTime > System.currentTimeMillis();
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }

    public int getReminderIntervalMinutes() {
        return reminderIntervalMinutes;
    }

    public void setReminderIntervalMinutes(int reminderIntervalMinutes) {
        this.reminderIntervalMinutes = reminderIntervalMinutes;
    }

    public void copyFrom(Note other) {
        if (other == null) return;
        this.title = other.title;
        this.value = other.value;
        this.date = other.date;
        this.tag = other.tag;
        this.valueJson = other.valueJson;
        this.attachments = other.attachments;
        this.reminderTime = other.reminderTime;
        this.reminderRepeat = other.reminderRepeat;
        this.isPinned = other.isPinned;
        this.reminderIntervalMinutes = other.reminderIntervalMinutes;
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
        c.setReminderTime(null);
        c.setReminderRepeat("NONE");
        return c;
    }
}
