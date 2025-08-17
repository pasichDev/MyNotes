package com.pasich.mynotes.data.model;

public class IndexFilter {

    private int indexTitle, indexValue;
    private long idNote;


    public IndexFilter(long idNote, int indexTitle, int indexValue) {
        this.idNote = idNote;
        this.indexTitle = indexTitle;
        this.indexValue = indexValue;

    }

    public long getIdNote() {
        return idNote;
    }

    public void setIdNote(long idNote) {
        this.idNote = idNote;
    }

    public int getIndexTitle() {
        return indexTitle;
    }

    public void setIndexTitle(int indexTitle) {
        this.indexTitle = indexTitle;
    }

    public int getIndexValue() {
        return indexValue;
    }

    public void setIndexValue(int indexValue) {
        this.indexValue = indexValue;
    }
}
