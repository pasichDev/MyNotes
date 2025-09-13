package com.pasich.mynotes.data.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

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
    private String backgroundData;


    @Ignore
    private boolean Checked;
    
    @Ignore
    private NoteBackground background;

    public Note create(String title, String value, long date, String tag) {
        this.title = title;
        this.tag = tag;
        this.value = value;
        this.date = date;
        this.Checked = false;
        this.backgroundData = "";
        this.background = null;
        return this;
    }

    public Note create(String title, String value, long date) {
        this.title = title;
        this.tag = "";
        this.value = value;
        this.date = date;
        this.Checked = false;
        this.backgroundData = "";
        this.background = null;
        return this;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return this.title;
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
    
    // Методи для роботи з фоном
    public String getBackgroundData() {
        return this.backgroundData != null ? this.backgroundData : "";
    }
    
    public void setBackgroundData(String backgroundData) {
        this.backgroundData = backgroundData;
        this.background = null; // Скидаємо кешований об'єкт
    }
    
    public NoteBackground getBackground() {
        if (background == null && backgroundData != null && !backgroundData.isEmpty()) {
            background = NoteBackground.fromJson(backgroundData);
        }
        if (background == null) {
            background = NoteBackground.createDefault();
        }
        return background;
    }
    
    public void setBackground(NoteBackground background) {
        this.background = background;
        this.backgroundData = background != null ? background.toJson() : "";
    }
    
    /**
     * Перевіряє чи встановлено кастомний фон (не DEFAULT)
     */
    public boolean hasCustomBackground() {
        NoteBackground bg = getBackground();
        return bg != null && bg.getType() != NoteBackground.BackgroundType.DEFAULT;
    }

}
