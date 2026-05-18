package com.pasich.mynotes.data.model;

public record HelpSection(
        int type, String title, String description, Integer iconRes, String additionalInfo) {
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_SECTION_TITLE = 1;
    public static final int TYPE_FEATURE = 2;
}
