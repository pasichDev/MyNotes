package com.pasich.mynotes.data.model.deprecated;


import com.google.gson.annotations.SerializedName;

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


}
