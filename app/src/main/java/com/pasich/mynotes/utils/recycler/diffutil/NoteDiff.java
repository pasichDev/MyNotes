package com.pasich.mynotes.utils.recycler.diffutil;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import com.pasich.mynotes.data.model.Note;
import dagger.hilt.android.scopes.ActivityScoped;
import java.util.Objects;

/** DiffUtil callback for comparing Note items in a RecyclerView list. */
@ActivityScoped
public class NoteDiff extends DiffUtil.ItemCallback<Note> {

    @Override
    public boolean areItemsTheSame(@NonNull Note oldItem, @NonNull Note newItem) {
        if (oldItem.getId() == 0 || newItem.getId() == 0) {
            return oldItem.getDate() == newItem.getDate()
                    && oldItem.getTitle().equals(newItem.getTitle())
                    && oldItem.getValue().equals(newItem.getValue());
        }
        return oldItem.getId() == newItem.getId();
    }

    @Override
    public boolean areContentsTheSame(@NonNull Note oldItem, @NonNull Note newItem) {
        return sameContent(oldItem, newItem);
    }

    /** True when both notes would render the same card. */
    public static boolean sameContent(@NonNull Note oldItem, @NonNull Note newItem) {
        return oldItem.getId() == newItem.getId()
                && Objects.equals(oldItem.getTitle(), newItem.getTitle())
                && Objects.equals(oldItem.getValue(), newItem.getValue())
                && Objects.equals(oldItem.getTag(), newItem.getTag())
                && oldItem.getDate() == newItem.getDate()
                && oldItem.isPinned() == newItem.isPinned()
                && Objects.equals(oldItem.getReminderTime(), newItem.getReminderTime())
                && Objects.equals(oldItem.getReminderRepeat(), newItem.getReminderRepeat())
                && Objects.equals(oldItem.getAttachments(), newItem.getAttachments());
    }
}
