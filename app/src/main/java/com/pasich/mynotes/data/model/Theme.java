package com.pasich.mynotes.data.model;

public class Theme {

    private final int Image;
    private boolean check;
    private final int THEME_ID;
    private final int THEME_STYLE;
    private final int colorStrokeSelected;

    public Theme(int Image, int theme_id, int theme_style, int colorStrokeSelected) {
        this.Image = Image;
        this.check = false;
        this.THEME_ID = theme_id;
        this.THEME_STYLE = theme_style;
        this.colorStrokeSelected = colorStrokeSelected;
    }

    public int getTHEME_STYLE() {
        return THEME_STYLE;
    }

    public int getId() {
        return THEME_ID;
    }

    public int getImage() {
        return Image;
    }

    public boolean isCheck() {
        return check;
    }

    public int getColorStrokeSelected() {
        return colorStrokeSelected;
    }

    public Theme setCheckReturn(boolean check) {
        this.check = check;
        return this;
    }
}
