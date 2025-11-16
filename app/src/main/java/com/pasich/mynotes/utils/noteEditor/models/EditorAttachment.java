package com.pasich.mynotes.utils.noteEditor.models;

public class EditorAttachment {
    public String url;
    public String name;
    public String extension;
    public long size;

    public EditorAttachment(String url, String name, String extension, long size) {
        this.url = url;
        this.name = name;
        this.extension = extension;
        this.size = size;
    }
}
