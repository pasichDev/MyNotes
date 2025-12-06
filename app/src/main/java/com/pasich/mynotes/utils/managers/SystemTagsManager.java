package com.pasich.mynotes.utils.managers;

import com.pasich.mynotes.data.model.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager for system-level tags.
 * Contains helper methods to identify and create system tags.
 */
public class SystemTagsManager {

    // System tag types
    public static final int SYSTEM_ACTION_ADD_TAG = 1;     // "Add tag" button
    public static final int SYSTEM_ACTION_ALL_NOTES = 2;   // "All notes"
    public static final int SYSTEM_ACTION_USER_TAG = 0;    // Regular user tag


    /**
     * Returns the built-in system tags.
     */
    public static List<Tag> getSystemTags() {
        List<Tag> systemTags = new ArrayList<>();

        Tag allNotesTag = new Tag();
        allNotesTag.setNameTag("allNotes");
        allNotesTag.setSystemAction(SYSTEM_ACTION_ALL_NOTES);
        allNotesTag.setSelected(true);

        systemTags.add(allNotesTag);
        return systemTags;
    }

    /**
     * Checks if a tag is system-defined.
     */
    public static boolean isSystemTag(Tag tag) {
        return tag.getSystemAction() == SYSTEM_ACTION_ADD_TAG ||  tag.getSystemAction() == SYSTEM_ACTION_ALL_NOTES;
    }

    /**
     * Checks if the tag represents the "Add tag" action.
     */
    public static boolean isAddTag(Tag tag) {
        return tag.getSystemAction() == SYSTEM_ACTION_ADD_TAG;
    }

    /**
     * Checks if the tag represents "All notes".
     */
    public static boolean isAllNotesTag(Tag tag) {
        return tag.getSystemAction() == SYSTEM_ACTION_ALL_NOTES;
    }

    /**
     * Creates the "Add tag" system entry.
     */
    public static Tag createAddTag() {
        Tag tag = new Tag();
        tag.setSystemAction(SYSTEM_ACTION_ADD_TAG);
        return tag;
    }

    /**
     * Creates the "All notes" system entry
     */
    public static Tag createAllNotesTag() {
        Tag tag = new Tag();
        tag.setNameTag("allNotes");
        tag.setSystemAction(SYSTEM_ACTION_ALL_NOTES);
        tag.setSelected(true);
        return tag;
    }
}
