package com.pasich.mynotes.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class Task {

    @PrimaryKey(autoGenerate = true)
    private int id;
    @NonNull
    private String title = "";
    private String description;
    @ColumnInfo(defaultValue = "0")
    private boolean isDone;
    @ColumnInfo(defaultValue = "0")
    private int categoryId;
    @ColumnInfo(defaultValue = "0")
    private long createdAt;
    @ColumnInfo(defaultValue = "0")
    private int position;
    private Long reminderTime;

    public Task() {}

    @Ignore
    public Task(String title, int categoryId) {
        this.title = title;
        this.categoryId = categoryId;
        this.isDone = false;
        this.createdAt = System.currentTimeMillis();
        this.position = 0;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isDone() { return isDone; }
    public void setDone(boolean done) { isDone = done; }
    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public Long getReminderTime() { return reminderTime; }
    public void setReminderTime(Long reminderTime) { this.reminderTime = reminderTime; }
}
