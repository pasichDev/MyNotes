package com.pasich.mynotes.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(tableName = "tags")
public class Tag {

    @PrimaryKey(autoGenerate = true)
    @SerializedName("a")
    public long id;

    @NonNull
    @ColumnInfo(name = "name")
    @SerializedName("b")
    private String nameTag = "";

    @ColumnInfo(name = "visibility")
    @SerializedName("c")
    private int visibility = 0;

    /**
     * SystemAction - System tag type (1) - add tag (2) - all notes (0) -
     * user tag
     */
    @ColumnInfo(name = "systemAction")
    @SerializedName("d")
    private int systemAction = 0;

    /**
     * Position - tag position for custom sorting
     */
    @ColumnInfo(name = "position")
    @SerializedName("e")
    private int position = 0;

    @Ignore
    private boolean selected = false;

    public Tag create(String nameTag, int systemAction) {
        this.nameTag = nameTag;
        this.systemAction = systemAction;
        return this;
    }

    public Tag create(String nameTag) {
        this.nameTag = nameTag;
        this.position = -1;
        return this;
    }

    @NonNull
    public String getNameTag() {
        return this.nameTag;
    }

    public void setNameTag(@NonNull String newNameTag) {
        this.nameTag = newNameTag;
    }

    public long getId() {
        return this.id;
    }

    public int getSystemAction() {
        return this.systemAction;
    }

    public void setSystemAction(int arg0) {
        this.systemAction = arg0;
    }

    public boolean getSelected() {
        return this.selected;
    }

    public void setSelected(boolean sel) {
        this.selected = sel;
    }

    public int getVisibility() {
        return this.visibility;
    }

    public void setVisibility(int arg0) {
        this.visibility = arg0;
    }

    public Tag setVisibilityReturn(int arg0) {
        this.visibility = arg0;
        return this;
    }

    public int getPosition() {
        return this.position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public Tag copy() {
        Tag t = new Tag();

        t.id = this.id;
        t.nameTag = this.nameTag;
        t.visibility = this.visibility;
        t.systemAction = this.systemAction;
        t.position = this.position;

        // selected Not part of the database, but important for the UI
        t.selected = this.selected;

        return t;
    }

}
