package com.pasich.mynotes.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "task_categories")
public class TaskCategory {

    @PrimaryKey(autoGenerate = true)
    private int id;
    @NonNull
    private String name = "";
    @NonNull
    @ColumnInfo(defaultValue = "#6750A4")
    private String colorHex = "#6750A4";
    @ColumnInfo(defaultValue = "0")
    private int position;

    public TaskCategory() {}

    @Ignore
    public TaskCategory(String name, String colorHex) {
        this.name = name;
        this.colorHex = colorHex;
        this.position = 0;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
