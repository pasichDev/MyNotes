package com.pasich.mynotes.data.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "trash")
public class TrashNote {

    @PrimaryKey(autoGenerate = true)
    public int id;

    private String title;
    private String value;
    private long date;

    @Ignore
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


}
