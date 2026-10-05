package com.pasich.mynotes.utils.navigation;

public final class NoteExtras {

    private NoteExtras() {} // no instances

    public static final String EXTRA_NEW_NOTE = "NewNote";
    public static final String EXTRA_ID_NOTE = "idNote";
    public static final String EXTRA_TAG_NOTE = "tagNote";

    /** Whether the note has attachments, so a reminder opens it in the extended editor. */
    public static final String EXTRA_HAS_ATTACHMENTS = "hasAttachments";
}
