package com.pasich.mynotes.ui.view.dialogs.main;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.pasich.mynotes.data.model.Tag;

/**
 * Carries a {@link Tag} in a fragment's arguments, so a dialog about a tag can be rebuilt by the
 * system after a rotation or after the process was killed.
 */
public final class TagArgs {

    private static final String KEY_ID = "tag.id";
    private static final String KEY_NAME = "tag.name";
    private static final String KEY_VISIBILITY = "tag.visibility";
    private static final String KEY_SYSTEM_ACTION = "tag.systemAction";
    private static final String KEY_POSITION = "tag.position";

    private TagArgs() {}

    public static void put(@NonNull Bundle args, @NonNull Tag tag) {
        args.putLong(KEY_ID, tag.getId());
        args.putString(KEY_NAME, tag.getNameTag());
        args.putInt(KEY_VISIBILITY, tag.getVisibility());
        args.putInt(KEY_SYSTEM_ACTION, tag.getSystemAction());
        args.putInt(KEY_POSITION, tag.getPosition());
    }

    /** Returns the tag put by {@link #put}, or {@code null} when there is none. */
    @Nullable
    public static Tag get(@Nullable Bundle args) {
        if (args == null || !args.containsKey(KEY_NAME)) return null;
        Tag tag = new Tag();
        tag.id = args.getLong(KEY_ID);
        String name = args.getString(KEY_NAME);
        tag.setNameTag(name == null ? "" : name);
        tag.setVisibility(args.getInt(KEY_VISIBILITY));
        tag.setSystemAction(args.getInt(KEY_SYSTEM_ACTION));
        tag.setPosition(args.getInt(KEY_POSITION));
        return tag;
    }
}
