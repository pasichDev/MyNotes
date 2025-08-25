package com.pasich.mynotes.utils.databinding;

import android.view.View;
import androidx.databinding.BindingAdapter;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.utils.managers.SystemTagsManager;

public class TagBindingAdapters {

    @BindingAdapter("visibilityForAddTag")
    public static void setVisibilityForAddTag(View view, Tag tag) {
        view.setVisibility(SystemTagsManager.isAddTag(tag) ? View.VISIBLE : View.GONE);
    }

    @BindingAdapter("visibilityForNonAddTag")
    public static void setVisibilityForNonAddTag(View view, Tag tag) {
        view.setVisibility(!SystemTagsManager.isAddTag(tag) ? View.VISIBLE : View.GONE);
    }

    @BindingAdapter("tagDisplayName")
    public static void setTagDisplayName(android.widget.TextView textView, Tag tag) {
        if (SystemTagsManager.isAllNotesTag(tag)) {
            textView.setText(textView.getContext().getString(com.pasich.mynotes.R.string.allNotes));
        } else {
            textView.setText(tag.getNameTag());
        }
    }
}
