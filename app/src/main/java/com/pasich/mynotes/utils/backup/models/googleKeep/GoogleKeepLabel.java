package com.pasich.mynotes.utils.backup.models.googleKeep;

import com.google.gson.annotations.SerializedName;

public class GoogleKeepLabel {
    @SerializedName("name")
    private String name;

    public GoogleKeepLabel(String name) {
        this.name = name;
    }

    public GoogleKeepLabel() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GoogleKeepLabel that = (GoogleKeepLabel) o;
        return name != null && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
