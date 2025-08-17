package com.pasich.mynotes.data.model;

public class Label {

    private final int Image;
    private boolean check;

    public Label(int Image) {
        this.Image = Image;
        this.check = false;
    }

    public int getImage() {
        return Image;
    }

    public boolean isCheck() {
        return check;
    }

    public Label setCheckReturn(boolean check) {
        this.check = check;
        return this;
    }
}
